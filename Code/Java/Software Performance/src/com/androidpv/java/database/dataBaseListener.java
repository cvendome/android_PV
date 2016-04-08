package com.androidpv.java.database;

import java.sql.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by kim on 3/4/2016.
 */
public class dataBaseListener {

    public dataBaseListener(){
        String url = "jdbc:mysql://localhost:3306/membership";
        String username = "root";
        String password = "";
        Statement stmt;
        ResultSet rs;
        ResultSet userRs;
        String oldFileName = "data.txt";
        BufferedReader br = null;
        BufferedWriter bw = null;

        //Working values
        String currentUser = "altonKim";
        String application = "AlarmKlock";
        String traceNumber = "1";
        String traceId = currentUser + application + traceNumber;


        System.out.println("Connecting database...");
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            System.out.println("Database connected!");
           try{
               stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                       ResultSet.CONCUR_UPDATABLE);
               userRs = stmt.executeQuery("SELECT * FROM applications WHERE username = '" + currentUser + "' and application = '" + application+"'");
               if (!userRs.isBeforeFirst() ) {
                   System.out.println("No data in applications");
                   userRs.moveToInsertRow();
                   userRs.updateString(2,currentUser);
                   userRs.updateString(3,application);
                   userRs.insertRow();
                   userRs.moveToCurrentRow();
               }
               stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                       ResultSet.CONCUR_UPDATABLE);
               userRs = stmt.executeQuery("SELECT * FROM traces WHERE traceId = '"+traceId+"'");
               if (!userRs.isBeforeFirst() ) {
                   System.out.println("No data in traces");
                   userRs.moveToInsertRow();
                   userRs.updateString(2,currentUser);
                   userRs.updateString(3,application);
                   userRs.updateString(4,traceId);
                   userRs.insertRow();
                   userRs.moveToCurrentRow();
               }
               stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                       ResultSet.CONCUR_UPDATABLE);
               rs = stmt.executeQuery("SELECT * FROM data WHERE traceId = '"+traceId+"'");
               if(rs.isBeforeFirst()){
                   System.out.println("Data already exists in traces");
               }
               else {
                   try {
                       System.out.println("Inputting to database");
                       rs = stmt.executeQuery("SELECT * FROM data");
                       br = new BufferedReader(new FileReader(oldFileName));
                       String line;
                       String[] splitline;
                       HashMap<String, ArrayList<String>> stack = new HashMap<>();
                       while ((line = br.readLine()) != null) {
                           splitline = line.split("::");
                           String[] temp = splitline[0].split(" ");
                           String currentName = temp[temp.length - 1];
                           if (splitline.length == 3) {
                               if (stack.containsKey(currentName)) {
                                   if (splitline[1].equals("methodStart")) {
                                       stack.get(currentName).add(splitline[2]);
                                   } else {
                                       rs.moveToInsertRow();
                                       rs.updateString(2, traceId);
                                       rs.updateString(3, currentName);
                                       rs.updateLong(4, Long.parseLong(stack.get(currentName).remove(0)));
                                       rs.updateLong(5, Long.parseLong(splitline[2]));
                                       rs.insertRow();
                                       rs.moveToCurrentRow();
                                       if (stack.get(currentName).size() == 0) {
                                           stack.remove(currentName);
                                       }
                                   }
                               } else {
                                   stack.put(currentName, new ArrayList<String>());
                                   stack.get(currentName).add(splitline[2]);
                               }
                           }
                       }
                       System.out.println("Completed!");
                   } catch (Exception e) {
                       System.out.println("Error: " + e.getMessage());
                       return;
                   } finally {
                       try {
                           if (br != null)
                               br.close();
                       } catch (IOException e) {
                           //
                       }
                       try {
                           if (bw != null)
                               bw.close();
                       } catch (IOException e) {
                           //
                       }
                   }
               }

           }
           catch (SQLException e){
               System.out.println("SQLException: " + e.getMessage());
               System.out.println("SQLState: " + e.getSQLState());
               System.out.println("VendorError: " + e.getErrorCode());
           }
        } catch (SQLException e) {
            System.out.println("SQLException: " + e.getMessage());
            System.out.println("SQLState: " + e.getSQLState());
            System.out.println("VendorError: " + e.getErrorCode());
            throw new IllegalStateException("Cannot connect the database!", e);
        }
    }
}
