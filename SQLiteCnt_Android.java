import java.io.File;
import java.sql.*;

/**
 * Created by Tristan on 28/10/19.
 */

public class SQLiteCnt_Android {
    private String TAG = "SQLiteConnex: ";
    private String dbPath = Main.ABS_PATH + File.separator+"SQL"+ File.separator + "ANFR_SQLite.db";
    private String dbPathProv = Main.ABS_PATH + File.separator+"SQL"+File.separator + "ANFR_SQLite_prov.db";
    private Connection connection = null;
    private Statement statement = null;

    public SQLiteCnt_Android() {
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            statement = connection.createStatement();
            createTables();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error at SQL connect()");
            e.printStackTrace();
        }
    }

    public void restore() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            statement = connection.createStatement();
            statement.execute("restore from '"+dbPath+"'");     //charger le fichier SQL existant
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error at SQL connect()");
            e.printStackTrace();
        }
    }

    public void restoreProv() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            statement = connection.createStatement();
            statement.execute("restore from '"+dbPathProv+"'");     //charger le fichier SQL existant
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error at SQL connect()");
            e.printStackTrace();
        }
    }


    public void close() {
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createTables() {
        sql_query("CREATE TABLE '20801' (ID INTEGER PRIMARY KEY AUTOINCREMENT, AnfrID INTEGER, adr_full TEXT, LAT REAL, LON REAL, Act TINYINT, syst_4 TEXT, datesAct_4 TEXT, syst_5 TEXT, datesAct_5 TEXT, Haut TINYINT, Azimuth TEXT)");
        sql_query("CREATE TABLE '20810' (ID INTEGER PRIMARY KEY AUTOINCREMENT, AnfrID INTEGER, adr_full TEXT, LAT REAL, LON REAL, Act TINYINT, syst_4 TEXT, datesAct_4 TEXT, syst_5 TEXT, datesAct_5 TEXT, Haut TINYINT, Azimuth TEXT)");
        sql_query("CREATE TABLE '20815' (ID INTEGER PRIMARY KEY AUTOINCREMENT, AnfrID INTEGER, adr_full TEXT, LAT REAL, LON REAL, Act TINYINT, syst_4 TEXT, datesAct_4 TEXT, syst_5 TEXT, datesAct_5 TEXT, Haut TINYINT, Azimuth TEXT)");
        sql_query("CREATE TABLE '20820' (ID INTEGER PRIMARY KEY AUTOINCREMENT, AnfrID INTEGER, adr_full TEXT, LAT REAL, LON REAL, Act TINYINT, syst_4 TEXT, datesAct_4 TEXT, syst_5 TEXT, datesAct_5 TEXT, Haut TINYINT, Azimuth TEXT)");
        sql_query("CREATE TABLE '99999' (ID INTEGER PRIMARY KEY AUTOINCREMENT, AnfrID INTEGER, adr_full TEXT, LAT REAL, LON REAL, Act TINYINT, syst_4 TEXT, datesAct_4 TEXT, syst_5 TEXT, datesAct_5 TEXT, OP TEXT, Azimuth TEXT)");

        sql_query("CREATE TABLE Version (ID INTEGER PRIMARY KEY AUTOINCREMENT, Version INTEGER, Date TEXT)");
        sql_query("CREATE TABLE android_metadata (locale TEXT)");
        sql_query("INSERT INTO android_metadata VALUES ('fr_FR')");
    }


    //ECRIRE LE FICHIER DEFINITIF
    public void saveFile() {
        try {
            Statement stmt  = this.connection.createStatement();
            stmt.executeUpdate("backup to '"+dbPath+"'");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //ECRIRE LE FICHIER PROVISOIRE
    public void saveFileProv() {
        try {
            Statement stmt  = this.connection.createStatement();
            stmt.executeUpdate("backup to '"+dbPathProv+"'");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // mise à jour de la version de la base
    public void updateDatabaseVersion(int version) {
        try {
            Statement stmt  = this.connection.createStatement();
            stmt.execute("PRAGMA user_version = "+version+";");
        } catch (SQLException e) {
            System.err.println(TAG + e.getMessage());
        }
    }


    public void sql_query(String query) {
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public ResultSet queryRs(String request) {
        ResultSet resultSet = null;
        try {
            Statement stt = connection.createStatement();
            resultSet = stt.executeQuery(request);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultSet;
    }



}
