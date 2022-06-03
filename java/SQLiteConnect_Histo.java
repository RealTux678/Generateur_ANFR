import java.io.File;
import java.sql.*;

/**
 * Created by Tristan on 09/04/21.
 */

public class SQLiteConnect_Histo {
    private String TAG = "SQLiteConnex: ";
    private String dbPath = Main.ABS_PATH + File.separator+"SQL"+File.separator+"ANFR"+File.separator;
    private Connection connection = null;
    private Statement statement = null;

    public SQLiteConnect_Histo() {
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

    public void restore(String file) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            statement = connection.createStatement();
            statement.execute("restore from '"+ dbPath +file+"'");     //charger le fichier SQL existant
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


    //ECRIRE LE FICHIER
    public void saveFile(String dbName) {
        File fdb = new File(dbPath + dbName);    //éffacer éventuels fichier SQL précédents. Inutile puisque le n° de version est incrémenté :!
        fdb.delete();

        try {
            Statement stmt  = this.connection.createStatement();
            stmt.executeUpdate("backup to '" + dbPath + dbName+"'");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void createTables() {
        sql_query("CREATE TABLE '20801' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        sql_query("CREATE TABLE '20810' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        sql_query("CREATE TABLE '20815' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        sql_query("CREATE TABLE '20820' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        sql_query("CREATE TABLE '99999' (ID INTEGER PRIMARY KEY AUTOINCREMENT, OP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        sql_query("CREATE TABLE Version (ID INTEGER PRIMARY KEY AUTOINCREMENT, Version INTEGER, Date TEXT)");
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

    public void sql_query(String query) {
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);
            stt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    //compter le nombre de lignes
    public int numberRows(String table) {
        int count = 0;
        String query = "SELECT COUNT(*) AS rowcount FROM '" + table + "'";
        try {
            Statement stt = connection.createStatement();
            ResultSet rs = stt.executeQuery(query);
            rs.next();
            count = rs.getInt("rowcount");
            rs.close();
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return count;
    }

    //compter le nombre de lignes
    public int numberRowsW(String table, String where) {
        int count = 0;
        String query = "SELECT COUNT(*) AS rowcount FROM '" + table + "' WHERE "+where;
        try {
            Statement stt = connection.createStatement();
            ResultSet rs = stt.executeQuery(query);
            rs.next();
            count = rs.getInt("rowcount");
            rs.close();
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return count;
    }


    public void insertData(String table, String cp, String insee, String sta, int AnfrID, String AnfrData, double LAT, double LON, int xg, int act, String actDate, String syst) {
        String query;
        query = "INSERT INTO '"+table+"' VALUES (NULL, '"+cp+"', '"+insee+"', '"+sta+"', "+AnfrID+", '"+AnfrData+"', '"+LAT+"', '"+LON+"', "+xg+", "+act+", '"+actDate+"', '"+syst+"')";
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);
            stt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
