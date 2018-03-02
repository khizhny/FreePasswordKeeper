package com.khizhny.freepasswordkeeper;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.khizhny.freepasswordkeeper.LoginActivity.LOG;

@SuppressWarnings("ConstantConditions")
class DbHelper extends SQLiteOpenHelper {

		static final String DATABASE_NAME = "database.db";
		private static final int DATABASE_VERSION = 1;

		private static final String TABLE_USERS = "users";
		private static final String TABLE_CATEGORIES = "categories";
		private static final String TABLE_ENTRIES = "entries";

		private SQLiteDatabase db;
		private static DbHelper instance;

		private DbHelper(Context context) { //private constructor
				super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		static synchronized DbHelper getInstance(Context context) {
				if (instance == null) {
						instance = new DbHelper(context);
				}
				return instance;
		}

		void open() {
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
				db.execSQL("CREATE TABLE "+TABLE_USERS+" (" +
								" `_id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
								" `name` TEXT NOT NULL," +
								" `name_encrypted` TEXT NOT NULL" +
								" )");

				db.execSQL("CREATE TABLE "+TABLE_CATEGORIES+" (\n" +
								" `_id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
								" `name` TEXT NOT NULL,\n" +
								" `user_id` INTEGER NOT NULL,\n" +
								" `parent_id` INTEGER,\n" +
								" FOREIGN KEY(user_id) REFERENCES users(_id)\n" +
								" )");

				db.execSQL("CREATE TABLE "+ TABLE_ENTRIES +" (\n" +
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

				Log.d(LOG, "db.onUpgrade() "+oldVersion+"to"+newVersion);
				switch (oldVersion){
						case 1:
								// db.execSQL("ALTER TABLE banks ADD COLUMN editable;");
								Log.d(LOG, "DB Updated from 1 to 2");
				}
				Log.d(LOG, "DATABASE UPGRADED.");
		}

		synchronized void addOrEditNode(Node node, boolean withChildren){
				String tableName="";
				if (node instanceof User) tableName= TABLE_USERS;
				if (node instanceof Category) tableName=TABLE_CATEGORIES;
				if (node instanceof Entry) tableName= TABLE_ENTRIES;

				if (node.id<0){ // new
						long rowId = getWritableDatabase().insertOrThrow(tableName, null, node.getContentValues());
						if (rowId >= 0) {
								Cursor c = getWritableDatabase().rawQuery("SELECT _id " +
												" FROM " + tableName+
												" WHERE ROWID=" + rowId, null);
								if (c.moveToFirst()) {
										node.id = c.getInt(0);
								}
								c.close();
						} else {
								Log.e(LOG, "Error while inserting new record");
						}
				}else { // existing
						getWritableDatabase().update(tableName,node.getContentValues(),"_id=?",new String[]{node.id+""});
				}

				if (withChildren){ // Saving child nodes as well

						if (node instanceof User) {
								addOrEditNode(((User) node).rootCategory,withChildren); // Users have just 1 root category
						}

						if (node instanceof Category){
								// saving all  Entries in category
								for (Entry e:((Category) node).entryList) {
										addOrEditNode(e, false); //
								}
								// saving all  SubCategories in category
								for (Category c:((Category) node).categoryList) {
										addOrEditNode(c, withChildren);
								}
						}

						// Entries has no child nodes
				}
		}

		/**
		 * Gets User from DB with bind root category
		 * @param userId -ID
		 * @param withChildren - If true all entries and subFolders is also loaded
		 * @return user
		 */
		@Nullable
		synchronized User getUser(int userId,boolean withChildren, String password, String login){
				Cursor c=getWritableDatabase().query(TABLE_USERS,new String[]{"name","name_encrypted"},"_id=?",new String[]{userId+""},null,null,null);
				User user=null;
				while (c.moveToNext()){
						user=new User(c.getString(0),userId);
						user.decrypter=new Decrypter(password,login);
						getRootCategory(user, withChildren);
				}
				c.close();

				return user;
		}

		/**
		 * Gets all Entries in category
		 * @param category Category
		 */
		private synchronized void getEntries(Category category){
				Cursor c=getWritableDatabase().query(TABLE_ENTRIES,new String[]{"_id","password","login", "url","comment","name"},"category_id=?",new String[]{category.id+""},null,null,null);
				while (c.moveToNext()){
						Decrypter d= category.user.decrypter;
						int entryId=c.getInt(0);

						String password=c.getString(1);
						String login=c.getString(2);
						String url=c.getString(3);
						String comment=c.getString(4);
						String name=c.getString(5);

						if (!name.equals("")) name=d.decrypt(name);
						if (!comment.equals("")) comment=d.decrypt(comment);
						if (!url.equals("")) url=d.decrypt(url);
						if (!login.equals("")) login=d.decrypt(login);
						if (!password.equals("")) password=d.decrypt(password);

						new Entry(category,password,login,name,comment,url,entryId);
				}
				c.close();
		}

		/**
		 * Gets all subfolders in category recursively
		 * @param parentCategory	-category
		 * @param withChildren	Include subfolders and Entries
		 */
		private synchronized void getSubCategories(Category parentCategory, boolean withChildren){
				Cursor c=getWritableDatabase().query(TABLE_CATEGORIES,
								new String[]{"name","_id"},
								"user_id=? and parent_id=?",
								new String[]{parentCategory.user.id+"",parentCategory.id+""},
								null,null,null);
				while (c.moveToNext()){
						String name=c.getString(0);
						int id=c.getInt(1);

						Decrypter d = parentCategory.user.decrypter;
						name=d.decrypt(name);

						Category category = new Category(parentCategory,name);
						category.id=id;

						if (withChildren) {
								getEntries(category);
								getSubCategories(category,withChildren);
						}
				}
				c.close();
		}

		private synchronized void getRootCategory(User user, boolean withChildren){
				Cursor c=getWritableDatabase().query(TABLE_CATEGORIES,
								new String[]{"_id"},
								"user_id=? and parent_id is null",new String[]{user.id+""},null,null,null);
				while (c.moveToNext()){
						Category rootCategory = user.rootCategory;
						rootCategory.id=c.getInt(0);
						if (withChildren) {
								getEntries(rootCategory);
								getSubCategories(rootCategory, withChildren);
						}
				}
				c.close();
		}

		synchronized List<User> getAllUsers(){
				List<User> result= new ArrayList<>();

				Cursor c=getWritableDatabase().query(TABLE_USERS,new String[]{"name","_id","name_encrypted"},null,null,null,null,null);
				while (c.moveToNext()){
						String userName=c.getString(0);
						int userId= c.getInt(1);
						User user = new User(userName,userId);
						user.name_encrypted=c.getString(2);
						getRootCategory(user,false); // getting root categories
						result.add(user);
				}
				c.close();
				return result;
		}

		 synchronized void deleteUserInfo(int userID){
				SQLiteDatabase db=getWritableDatabase();
				db.execSQL("DELETE FROM "+ TABLE_ENTRIES +" WHERE category_id IN (SELECT _id FROM categories WHERE user_id="+userID+")");
				db.execSQL("DELETE FROM "+TABLE_CATEGORIES+" WHERE user_id=" + userID);
				db.execSQL("DELETE FROM "+TABLE_USERS+" WHERE _id=" + userID);
		}

		synchronized void deleteEntry(Entry entry){
				//removing from db
				SQLiteDatabase db=getWritableDatabase();
				db.execSQL("DELETE FROM "+ TABLE_ENTRIES +" WHERE _id="+entry.id);
				// removing from list
				entry.parent.entryList.remove(entry);
		}

		/**
		 * Delete category with all children from db and from the parent category list
		 * @param category - Category 2 delete
		 */
		synchronized void deleteCategory(Category category){
				//removing entries first
				while (category.entryList.size()>0){
						deleteEntry(category.entryList.get(0));
				}
				// removing subfolders
				while (category.categoryList.size()>0){
						deleteCategory(category.categoryList.get(0));
				}

				//removing from db
				SQLiteDatabase db=getWritableDatabase();
				db.execSQL("DELETE FROM "+TABLE_CATEGORIES+" WHERE _id="+category.id);
				// removing from list
				(category.parent).categoryList.remove(category);
		}
}
