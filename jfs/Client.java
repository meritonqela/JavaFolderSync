package SyncFiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Vector;

/**  --- Client Side ---
 * Reference: https://github.com/btannous/JavaFileSync */

public class Client {
    private static String dirName;
    private static String serverIP;
    private static String fullDirName;
    private static int PORT_NUMBER;
    private static final String DONE = "DONE";
    private static Socket sock;
    private static ObjectInputStream ois;
    private static ObjectOutputStream oos;
    private static int fileCount = 0;

    public Client(String dirName, String fullDirName, String serverIP, int port) {
        Client.dirName = dirName;
        Client.serverIP = serverIP;
        Client.fullDirName = fullDirName;
        PORT_NUMBER = port;

        System.out.println("Klienti është zgjedhur!");
        System.out.println("Folderi për sinkronizim: " + dirName);
        System.out.println("Server IP: " + serverIP);
    }

    public void runClient() throws Exception {

        sock = new Socket(serverIP, PORT_NUMBER);
        oos = new ObjectOutputStream(sock.getOutputStream()); // send directory name to server
        oos.writeObject(new String(dirName));
        oos.flush();

        ois = new ObjectInputStream(sock.getInputStream());
        if(dirName.equalsIgnoreCase("-ls")) {
            Vector<String> directories = (Vector<String>) ois.readObject();
            //System.out.println("The directories available are: ");
            System.out.println("Folderat e disponueshëm janë: \n");
            for (int x = 0; x < directories.size(); x++) {
                System.out.print(directories.elementAt(x) + " ");
            }
            System.out.println();
            System.out.println();
        } else {
            System.out.print("Syncing..");
            // receive if this directory exists
            Boolean fExists = (Boolean) ois.readObject();

            File baseDir = new File(fullDirName); // skipping the base dir as it already should be set up on the server
            String[] children = baseDir.list();

            for (int i=0; i < children.length; i++) {
                visitAllDirsAndFiles(new File(baseDir, children[i]));
            }
            Vector<String> vecDONE = new Vector<String>();
            vecDONE.add(DONE);
            oos.writeObject(vecDONE);
            oos.flush();
            reinitConn();

            if(fExists)
                updateFromServer();

            System.out.println();
            System.out.println("--------Sinkronizimi përfundoi!----------");
        }
        oos.close();
        ois.close();
        sock.close();
    }

    // Process all files and directories under dir
    private static void visitAllDirsAndFiles(File dir) throws Exception{
        if (fileCount % 20 == 0) {
            System.out.print(".");
            fileCount = 0;
        }
        fileCount++;
        Vector<String> vec = new Vector<String>();
        vec.add(dir.getName());
        vec.add(dir.getAbsolutePath().substring((dir.getAbsolutePath().indexOf(fullDirName) + fullDirName.length())));

        if(dir.isDirectory()) {
            oos.writeObject(vec);
            oos.flush();
            reinitConn();

            ois.readObject();
        } else {
            vec.add(new Long(dir.lastModified()).toString());
            oos.writeObject(vec);
            oos.flush();
            reinitConn();
            // receive SEND or RECEIVE
            Integer updateToServer = (Integer) ois.readObject(); //if true update server, else update from server

            if (updateToServer == 1) {  // send file to server
                sendFile(dir);

                ois.readObject(); // make sure server got the file

            } else if (updateToServer == 0) { // update file from server.
                dir.delete(); // first delete the current file

                oos.writeObject(new Boolean(true)); // send "Ready"
                oos.flush();

                receiveFile(dir);

                oos.writeObject(new Boolean(true)); // send back ok
                oos.flush();

                Long updateLastModified = (Long) ois.readObject(); // update the last modified date for this file from the server
                dir.setLastModified(updateLastModified);

            } // no need to check if update to server == 2 because we do nothing here
        }
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                visitAllDirsAndFiles(new File(dir, children[i]));
            }
        }
    }

    private static void sendFile(File dir) throws Exception {
        byte[] buff = new byte[sock.getSendBufferSize()];
        int bytesRead = 0;

        InputStream in = new FileInputStream(dir);

        while((bytesRead = in.read(buff))>0) {
            oos.write(buff,0,bytesRead);
        }
        in.close();
        // after sending a file you need to close the socket and reopen one.
        oos.flush();
        reinitConn();

        //		printDebug(true, dir);
    }

    private static void receiveFile(File dir) throws Exception {
        FileOutputStream wr = new FileOutputStream(dir);
        byte[] outBuffer = new byte[sock.getReceiveBufferSize()];
        int bytesReceived = 0;
        while((bytesReceived = ois.read(outBuffer))>0) {
            wr.write(outBuffer,0,bytesReceived);
        }
        wr.flush();
        wr.close();

        reinitConn();

        //		printDebug(false, dir);
    }

    private static void updateFromServer() throws Exception {
        Boolean isDone = false;
        Boolean nAll = false;
        while(!isDone) {
            if (fileCount % 20 == 0) {
                System.out.print(".");
                fileCount = 0;
            }
            fileCount++;
            String path = (String) ois.readObject();

            if(path.equals(DONE)) {
                isDone = true;
                break;
            }

            oos.writeObject(new Boolean(true));
            oos.flush();

            File newFile = new File(fullDirName + path);

            Boolean isDirectory = (Boolean) ois.readObject();

            oos.writeObject(new Boolean(newFile.exists()));
            oos.flush();
            if (!newFile.exists()) {
                ois.readObject();
                String userInput = null;
                if (!nAll) {
                    if (isDirectory) {
                        System.out.println(" KONFLIKT! Folderi ekziston në server por jo në këtë klient.");
                        System.out.println("Dëshironi të fshini folderin në server (nëse jo, folderi do të kopjohet në këtë klient)?");
                        System.out.println("Jo - për të gjithë folderat. Do të krijoj një kopje të serverit në këtë klient");
                    } else {
                        System.out.println(" KONFLIKT! Fajlli ekziston në server por jo në këtë klient.");
                        System.out.println("Dëshironi të fshini fajllin në server (nëse jo, fajlli do të kopjohet në këtë klient)?");
                        System.out.println("Jo - për të gjithë fajllat. Do të krijoj një kopje të serverit në këtë klient");
                    }
                    System.out.println("Shtypni: [y] për PO, [n] për JO, [a] për (JO për të gjithë fajllat) ");
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                    try {
                        userInput = br.readLine();
                    } catch (IOException ioe) {
                        System.out.println("Ju nuk keni shtyp një vlerë korrekte, nuk do të merret asnjë veprim.");
                    }
                } else // if n to all then just set input to n!
                    userInput = "n";
                if (userInput.equalsIgnoreCase("a") || userInput.equalsIgnoreCase("'a'")) {
                    nAll = true;
                    userInput = "n";
                }
                if (userInput.equalsIgnoreCase("y") || userInput.equalsIgnoreCase("'y'")) {
                    if (isDirectory) {
                        oos.writeObject(new Boolean(true)); // reply with yes to delete the server's copy
                        oos.flush();
                    } else {
                        oos.writeObject(new Integer(1));
                        oos.flush();
                    }
                } else if (userInput.equalsIgnoreCase("n") || userInput.equalsIgnoreCase("'n'")) {
                    if (isDirectory) {
                        newFile.mkdir();
                        oos.writeObject(new Boolean(false));
                        oos.flush();
                    } else {
                        oos.writeObject(new Integer(0));
                        oos.flush();
                        receiveFile(newFile);

                        oos.writeObject(new Boolean(true));
                        oos.flush();

                        Long lastModified = (Long) ois.readObject();
                        newFile.setLastModified(lastModified);

                        oos.writeObject(new Boolean(true));
                        oos.flush();
                    }
                } else {
                    if (isDirectory) {
                        oos.writeObject(new Boolean(false));
                        oos.flush();
                    } else {
                        oos.writeObject(new Integer(2));
                        oos.flush();
                    }
                }
            }
        }
    }


    private static void reinitConn() throws Exception {
        ois.close();
        oos.close();
        sock.close();
        sock = new Socket(serverIP, PORT_NUMBER);
        ois = new ObjectInputStream(sock.getInputStream());
        oos = new ObjectOutputStream(sock.getOutputStream());
    }

}