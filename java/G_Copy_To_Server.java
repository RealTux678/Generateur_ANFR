import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Tristan on 20/06/21.
 */

public class G_Copy_To_Server {

    private String TAG = "CopyToServ ";

    public G_Copy_To_Server(String type) {
        if (type.equals("ANFR")) {
            copySqlFile();                  //ANFR_SQLite.db.xz
        } else if (type.equals("DIFF")) {
            leafletFile("diff.js");
        } else if (type.equals("DIFF_SUP")) {
            leafletFile("diff_sup.js");
        }
    }



    // FICHIER ANFR
    private void copySqlFile() {
        if (checkFile(Main.ABS_PATH +"/SQL/ANFR_SQLite.db.xz")) {
            //éffacer ancien fichier dans la destination
            File f = new File(Main.SERVER_FILES_PATH + "/files/downloads/ANFR_SQLite.db.xz");
            f.delete();

            //copier le fichier vers le serveur
            try {
                Files.copy(Paths.get(Main.ABS_PATH +"/SQL/ANFR_SQLite.db.xz"), Paths.get(Main.SERVER_FILES_PATH + "/files/downloads/ANFR_SQLite.db.xz"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(Main.ANSI_RED + "Erreur: Fichier ANFR_SQLite.db.xz manquant" + Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur: Fichier ANFR_SQLite.db.xz manquant");
        }
    }



    // Fichier JS
    private void leafletFile(String file) {
        // faire la manip seulement si le fichier source existe !
        if (checkFile(Main.ABS_PATH + "/Generated/"+file)) {
            //éffacer ancien fichier dans la destination
            File f = new File(Main.SERVER_FILES_PATH + "/data/"+file);
            f.delete();

            //copier le fichier vers le serveur
            try {
                Files.copy(Paths.get(Main.ABS_PATH + "/Generated/"+file), Paths.get(Main.SERVER_FILES_PATH + "/data/"+file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(Main.ANSI_RED + "Erreur: Fichier "+file+" manquant !" + Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur: Fichier "+file+" manquant !");
        }
    }





    /***********************************************************************************************
     *                                  METHODES COMMUNES
     **********************************************************************************************/

    // vérifier la présence d'un fichier
    private boolean checkFile(String path) {
        File f = new File(path);
        return f.exists();
    }



}
