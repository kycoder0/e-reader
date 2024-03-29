package com.trevor.e_reader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.Buffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import com.opencsv.CSVReader;

public class Books extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;

    public static final String AVAILABLE_TABLE_NAME = "available"; // books that can be downloaded
    public static final String KEY_ID = "_id"; // id of book
    public static final String KEY_TITLE = "title";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_URL = "url";
    public static final String KEY_IS_DOWNLOADED = "is_downloaded";


    public static final String DOWNLOADED_TABLE_NAME = "downloaded"; // downloaded books
    // downloaded table uses the keys above as well as these
    public static final String KEY_POSITION = "position"; // page user is on
    public static final String KEY_PATH = "file_path";  // path to file on phone
    public static final String KEY_DATE_LAST_READ = "date_read"; // date last read

    // id of books to read from in raw
    public int listOfBooksID;

    Context context;
    // constructor
    public Books(Context c, String DATABASE_NAME, int listOfBooksID){
        super(c,DATABASE_NAME, null, DATABASE_VERSION);
        this.listOfBooksID = listOfBooksID;
        context = c;
    }

    @Override
    public void onCreate(SQLiteDatabase db){
        // create available books table
        db.execSQL("CREATE TABLE " + AVAILABLE_TABLE_NAME + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_TITLE + " TEXT,"
                + KEY_AUTHOR + " TEXT,"
                + KEY_URL + " TEXT,"
                + KEY_IS_DOWNLOADED + " TEXT )");

        // create downloaded books table
        db.execSQL("CREATE TABLE " + DOWNLOADED_TABLE_NAME + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_TITLE + " TEXT,"
                + KEY_AUTHOR + " TEXT,"
                + KEY_URL + " TEXT,"
                + KEY_POSITION + " TEXT,"
                + KEY_PATH + " TEXT,"
                + KEY_DATE_LAST_READ + " TEXT)" );

        // initialize books
        initializeAvailableBooks(db);
    }

    // get available books from resources
    public void initializeAvailableBooks(SQLiteDatabase db) {

        // for every book, add it to the db
        ArrayList<Book> books = readAvailableBooks();
        for (Book b : books) {
            addBook(b, AVAILABLE_TABLE_NAME, db);
        }
    }

    // read the books that can be downloaded from raw file
    private ArrayList<Book> readAvailableBooks() {
        ArrayList<Book> books = new ArrayList<>();
        try {
            // open the raw file
            InputStream is = context.getResources().openRawResource(this.listOfBooksID);
            // make a reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            String line = "";
            // get the lines and parse out title, author, and url
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                Book book = new Book();
                book.setTitle(columns[0]);
                book.setAuthor(columns[1]);
                book.setUrl(columns[2]);
                // add the book
                books.add(book);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // return list of books
        return books;
    }
    public void addBook(Book book, String TABLE_NAME, SQLiteDatabase db) { ;
        ContentValues values = new ContentValues();

        // if the book is going into the downloaded table
        if (TABLE_NAME.equals(DOWNLOADED_TABLE_NAME)) {
            values.put(KEY_ID, book.getId());
            values.put(KEY_DATE_LAST_READ, book.getDateLastRead().toString());
            values.put(KEY_POSITION, book.getPosition());
            values.put(KEY_PATH, book.getPath());
        }
        else {
            // if not, put that it isn't downloaded
            values.put(KEY_IS_DOWNLOADED, "false");
        }

        // always insert title, author, and url
        values.put(KEY_TITLE, book.getTitle());
        values.put(KEY_AUTHOR, book.getAuthor());
        values.put(KEY_URL, book.getUrl());
        db.insert(TABLE_NAME, null, values);
    }
    // user is upgrading to a new version of the app with
    // a higher DATABASE_VERSION
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        // ideally should copy data over, but ignore for now
        db.execSQL("DROP TABLE IF EXISTS "+ DOWNLOADED_TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS "+ AVAILABLE_TABLE_NAME);
        onCreate(db);
    }
    
    // update book in db
    public void updateBook(Book b, boolean downloaded ) {
        SQLiteDatabase db = this.getWritableDatabase();

        // where key_id = our id
        String strFilter =  KEY_ID + "=?";
        ContentValues values = new ContentValues();

        // put title, author, and url
        values.put(KEY_TITLE, b.getTitle());
        values.put(KEY_AUTHOR, b.getAuthor());
        values.put(KEY_URL, b.getUrl());

        // record to update
        String[] whereArgs = {b.getId()};

        // if downloaded, put extra values
        if (downloaded){
            values.put(KEY_POSITION, b.getPosition());
            values.put(KEY_PATH, b.getPath());
            values.put(KEY_DATE_LAST_READ, b.getDateLastRead().toString());
            db.update(DOWNLOADED_TABLE_NAME, values, strFilter, whereArgs);
        }
        else {
            db.update(AVAILABLE_TABLE_NAME, values, strFilter, whereArgs);
        }

        db.close();
    }

    public void deleteBook(String id) {
        // deletes the file on the phone
        deleteBookFile(id);

        SQLiteDatabase db = this.getWritableDatabase();

        // where key_id = our id
        String filter = KEY_ID +"=?";

        String whereArgs[] = {id};

        // delete the book from db
        db.delete(DOWNLOADED_TABLE_NAME, filter, whereArgs);
        db.close();

    }

    // deletes the file on the phone for a given book
    public boolean deleteBookFile(String id) {
        Book book = this.getBooks(id,DOWNLOADED_TABLE_NAME).get(0);

        String path = book.getPath();

        File bookFile = new File(path);

        // return if the book was successfully deleted
        return bookFile.delete();
    }

    // get the book last read
    public Book getLastReadBook() {
        Book book = new Book();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor;

        // sql statement to select the most recent date
        cursor = db.rawQuery("Select " + KEY_ID + " From " + DOWNLOADED_TABLE_NAME
                            + " GROUP BY " + KEY_ID
                            + " ORDER BY " + KEY_DATE_LAST_READ + " DESC"
                            + " LIMIT 1", null);

        // anything to display?
        if(cursor.moveToFirst()){
            do {
                // get the id and path
                String id = cursor.getString(cursor.getColumnIndex(KEY_ID));
                String path = "";

                // set the id and path
                book.setId(id);
                book.setPath(path);
            } while (cursor.moveToNext());
        }
        else {
            book = null;
        }
        cursor.close();
        return book;
    }
    public ArrayList<Book> getBooks(String filter, String TABLE_NAME ) {
        ArrayList<Book> books = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor;

        // no filter?
        if (filter == null || filter.length() == 0) {
            // no filter
            cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
            // --  try db.query instead? --
        } else {
            // convert our filter string into an "array" for the query params

            String[] params = {filter};
            cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + KEY_ID + " = ?", params);

        }

        // anything to display?
        if(cursor.moveToFirst()){
            do {
                // get the id, title, author, url, and path
                String id = cursor.getString(cursor.getColumnIndex(KEY_ID));
                String title = cursor.getString(cursor.getColumnIndex(KEY_TITLE));
                String author = cursor.getString(cursor.getColumnIndex(KEY_AUTHOR));
                String url = cursor.getString(cursor.getColumnIndex(KEY_URL));
                String path = "";
                Date date = new Date();

                int position = 0;

                // if we're getting books from the downloaded table
                if (TABLE_NAME.equals("downloaded")) {
                    // get the position and path as well
                    position = Integer.parseInt(cursor.getString(cursor.getColumnIndex(KEY_POSITION)));
                    path = cursor.getString(cursor.getColumnIndex(KEY_PATH));
                }

                // set the books values and add it to the list
                Book book = new Book();
                book.setId(id);
                book.setTitle(title);
                book.setAuthor(author);
                book.setUrl(url);
                book.setDate(date);
                book.setPosition(position);
                book.setPath(path);
                books.add(book);
            } while (cursor.moveToNext());
        }
        cursor.close();

        // return list of books
        return books;
    }
}