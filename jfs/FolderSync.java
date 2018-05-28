package SyncFiles;

import java.io.File;
import java.util.Scanner;

/** Folder Synchronization */

public class FolderSync {
    private static String folderName;
    private static String serverIP;
    private static int PORT;
    private static Scanner s;
    static Client client = null;
    public static void main(String[] args) throws Exception {
        s = new Scanner(System.in);
        getUserInput();
        File file;

        if (folderName.equalsIgnoreCase("-ls")){
            client(folderName,"", serverIP, PORT);
            getFolderName();
        }
            file = new File(folderName);
            if (!file.exists()) {
                file.mkdir();
            }

           String sc = "sync";
            while (!sc.equalsIgnoreCase("c")){
                if (sc.equalsIgnoreCase("sync")) {
                    client(folderName, file.getAbsolutePath(), serverIP, PORT);
                }
                System.out.println("Shtypni: [sync] për sinkronizim ose [c] për të mbyllur aplikacionin.");
                sc = s.nextLine().trim();
            }

    }
    private static void client(String folderName, String path, String serverIP, int port) throws Exception{
        client = new Client(folderName, path, serverIP, port);
        client.runClient();
    }

    private static void getUserInput(){
        String userInput = "";
        
        System.out.println("Përdorimi: [folderName ose -ls] [serverIP] [port]");
        userInput = s.nextLine().trim();
        String[] cmd = userInput.split("\\s+");

        folderName = cmd[0];
        serverIP = cmd[1];
        PORT = Integer.parseInt(cmd[2]);
    }
    private static void getFolderName(){
        System.out.print("Emri i folderit: ");
        String fName = s.nextLine().trim();
        folderName = fName;
    }
}
