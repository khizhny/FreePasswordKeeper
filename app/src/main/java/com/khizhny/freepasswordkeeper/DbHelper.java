package com.khizhny.freepasswordkeeper;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.khizhny.freepasswordkeeper.LoginActivity.LOG;


public class DbHelper extends SQLiteOpenHelper {

		private static final String DATABASE_NAME = "database.db";
		private static final int DATABASE_VERSION = 1;
		private SQLiteDatabase db;
		private static DbHelper instance;

		private DbHelper(Context context) { //private constructor
				super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		public static synchronized DbHelper getInstance(Context context) {
				if (instance == null) {
						instance = new DbHelper(context);
				}
				return instance;
		}

		public void open() {
				try {
						this.db = getWritableDatabase();
				} catch (Exception e){
						e.printStackTrace();
						Log.e(LOG,"Incompatible db version found");
				}
		}

		public void close() {
				if (db != null)	db.close();
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
				// creating users table
				db.execSQL("CREATE TABLE users (" +
								" `_id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
								" `name` TEXT NOT NULL," +
								" `name_encrypted` TEXT NOT NULL" +
								" )");

				db.execSQL("CREATE TABLE categories (\n" +
								" `_id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
								" `name` TEXT NOT NULL,\n" +
								" `user_id` INTEGER NOT NULL,\n" +
								" `parent_id` INTEGER NOT NULL,\n" +
								" FOREIGN KEY(user_id) REFERENCES users(_id)\n" +
								" )");

				db.execSQL("CREATE TABLE entries (\n" +
								" `_id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
								" `password` TEXT NOT NULL,\n" +
								" `login` TEXT NOT NULL,\n" +
								" `url` TEXT NOT NULL,\n" +
								" `comment` TEXT NOT NULL,\n" +
								" `name` TEXT NOT NULL,\n" +
								" `category_id` INTEGER NOT NULL,\n" +
								" FOREIGN KEY(category_id) REFERENCES categories(_id)\n" +
								" )");
		}

		@Override
		public void onConfigure(SQLiteDatabase database) {
						database.setForeignKeyConstraintsEnabled(true);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

				Log.d(LOG, "dbhelper.onUpgrade() "+oldVersion+"to"+newVersion);
				switch (oldVersion){
						case 1:
								// db.execSQL("ALTER TABLE banks ADD COLUMN editable;");
								Log.d(LOG, "DB Updated from 1 to 2");
				}
				Log.d(LOG, "DATABASE UPGRADED.");
		}

		public synchronized void addOrEditUser(User user){
				if (user.id<0){ // new user
						long rowId = getWritableDatabase().insertOrThrow("users", null, user.getContentValues());
						if (rowId >= 0) {
								Cursor c = getWritableDatabase().rawQuery("SELECT _id " +
												"FROM users " +
												"WHERE ROWID=" + rowId, null);
								if (c.moveToFirst()) {
										user.id = c.getInt(0);
								}
								c.close();
						} else {
								Log.e(LOG, "Error while inserting new User");
						}
				}else { // existing user
						getWritableDatabase().update("users",user.getContentValues(),"_id=?",new String[]{user.id+""});
				}
		}

		public synchronized User getUser(int userId){
				Cursor c=getWritableDatabase().query("users",new String[]{"name"},"_id=?",new String[]{userId+""},null,null,null);
				while (c.moveToNext()){
						return new User(c.getString(0),userId);
				}
				c.close();
				return null;
		}

		private synchronized void getEntries(Category category){
				Cursor c=getWritableDatabase().query("entries",new String[]{"_id","password","login", "url","comment","name"},"category_id=?",new String[]{category.id+""},null,null,null);
				while (c.moveToNext()){
						int entryId=c.getInt(0);
						String password=c.getString(1);
						String login=c.getString(2);
						String url=c.getString(3);
						String comment=c.getString(4);
						String name=c.getString(5);
						Entry entry = new Entry(category,password,login,name);
						entry.id=entryId;
						entry.url=url;
						entry.comment=comment;

				}
				c.close();
		}

		private synchronized void getSubCategories(Category parentCategory){
				Cursor c=getWritableDatabase().query("categories",new String[]{"name","_id"},"user_id=? and parent_id=?",new String[]{parentCategory.user.id+"",parentCategory.id+""},null,null,null);
				while (c.moveToNext()){
						String categoryName=c.getString(0);
						Category category = new Category(parentCategory, categoryName);
						category.id=c.getInt(1);
						getSubCategories(category);
						getEntries(category);
				}
				c.close();
		}

		private synchronized void getRootCategory(User user){
				Cursor c=getWritableDatabase().query("categories",
								new String[]{"name","_id"},
								"user_id=? and parent_id=user_id",new String[]{user.id+""},null,null,null);
				while (c.moveToNext()){
						Category rootCategory = new Category(user);
						rootCategory.id=c.getInt(1);
						rootCategory.name=c.getString(0);
						getSubCategories(rootCategory);
						getEntries(rootCategory);
				}
				c.close();
		}

		public synchronized List<User> getAllUsers(){
				List<User> result=new ArrayList<User>();

				Cursor c=getWritableDatabase().query("users",new String[]{"name","_id","name_encrypted"},null,null,null,null,null);
				while (c.moveToNext()){
						String userName=c.getString(0);
						int userId= c.getInt(1);
						User user = new User(userName,userId);
						user.name_encrypted=c.getString(2);
						getRootCategory(user); // getting root categories
						result.add(user);
				}
				c.close();
				return result;
		}

		public synchronized void deleteUserInfo(int userID){
				SQLiteDatabase db=getWritableDatabase();
				db.execSQL("DELETE FROM entries WHERE category_id IN (SELECT _id FROM categories WHERE user_id="+userID+")");
				db.execSQL("DELETE FROM categories WHERE user_id=" + userID);
				db.execSQL("DELETE FROM users WHERE _id=" + userID);
		}


}
