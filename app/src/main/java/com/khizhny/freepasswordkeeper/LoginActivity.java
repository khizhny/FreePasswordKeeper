package com.khizhny.freepasswordkeeper;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class LoginActivity extends AppCompatActivity implements OnClickListener{

		public static final String LOG = "PASSKEEPER";
		private static final int MIN_USERNAME_LENGTH = 0;
		private static final int MIN_USER_PASS_LENGHT = 8;

		// UI references.
		private Spinner usersView;
		private EditText passwordView;
		private DbHelper db;
		private AlertDialog alertDialog;
		private List<User> usersList;


		@Override
		protected void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				setContentView(R.layout.activity_login);
				// Set up the login form.
				usersView = findViewById(R.id.login);

				passwordView = findViewById(R.id.password);
				passwordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
						@Override
						public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
								if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
										attemptLogin((User) usersView.getSelectedItem(),passwordView.getText().toString());
										return true;
								}
								return false;
						}
				});

				db=DbHelper.getInstance(this);
				db.open();
				refreshUserList();

				if (savedInstanceState!=null) {
						passwordView.setText(savedInstanceState.getString("user_pass"));
						usersView.setSelection(savedInstanceState.getInt("user_index"));
				}

		}

		@Override
		protected void onResume() {
				super.onResume();

				if (usersList.size()==0) showNewUserDialog();
		}

		@Override
		public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
				super.onSaveInstanceState(outState, outPersistentState);
				outState.putInt("user_index",usersView.getSelectedItemPosition());
				outState.putString("user_pass",passwordView.getText().toString());
		}

		@Override
		protected void onStop() {
				super.onStop();
				if (alertDialog!=null) {
						if (alertDialog.isShowing()) alertDialog.dismiss();
				}
				db.close();
		}

		private void attemptLogin(User user, String pass) {
				if (user!=null) {
						if (user.name.length() < 3) {
								Toast.makeText(this, "User name is too short.", Toast.LENGTH_LONG).show();
								return;
						}
						if (pass.length() < 8) {
								Toast.makeText(this, "Use longer password. At least 8 chars.", Toast.LENGTH_LONG).show();
								return;
						}

						user.decrypter=new Decrypter(pass,user.name);
						if (user.checkPassword()) {
								Toast.makeText(this, "Password is correct", Toast.LENGTH_LONG).show();
								// TODO open next activity
								Intent intent = new Intent(this, MainActivity.class);
								intent.putExtra("user_id", user.id );
								intent.putExtra("password", pass);
								intent.putExtra("login", user.name);
								startActivity(intent);
						} else{
								Toast.makeText(this, "Password is wrong", Toast.LENGTH_LONG).show();
				    }
				}
		}

			private void refreshUserList(){
				usersList=db.getAllUsers();
				usersView.setAdapter(new  ArrayAdapter(this,android.R.layout.simple_spinner_item, usersList));
		}



		private void showNewUserDialog() {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage("Welcome");
				builder.setView(R.layout.dialog_new_user);
				builder.setPositiveButton("Save",
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {

											  String userName=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
												String userPass=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
												if (userName.length() < MIN_USERNAME_LENGTH) {
														Toast.makeText(getApplicationContext(),"Too short name", Toast.LENGTH_SHORT).show();
												}else{
														if (userPass.length() < MIN_USER_PASS_LENGHT) {
																Toast.makeText(getApplicationContext(),"Password too short", Toast.LENGTH_SHORT).show();
														}else{
																alertDialog.dismiss();
																User user=new User(userName,-1);
																user.decrypter=new Decrypter(userPass,userName);
																user.name_encrypted=user.decrypter.encrypt(user.name);
																addTestRecords(user);
																db.addOrEditNode(user,true);
																refreshUserList();
																attemptLogin(user,userPass);
														}

												}
										}
								});
				alertDialog=builder.create();
				alertDialog.show();
		}

		@Override
		public void onClick(View v) {
				switch (v.getId()){
						case R.id.deleteUser:
								User u = (User) usersView.getSelectedItem();
								if (u!=null) {
										db.deleteUserInfo(u.id);
										refreshUserList();
								}
								break;
						case R.id.addUser:
								showNewUserDialog();
								break;
						case R.id.try_to_login:
								attemptLogin((User) usersView.getSelectedItem(),passwordView.getText().toString());
								break;
				}

		}

		private void addTestRecords(User u){
					Category c1= new Category(u.rootCategory,"Folder1");
					Category c2= new Category(u.rootCategory,"Folder2");
					Category c3= new Category(u.rootCategory,"Folder3");

					Category sc1= new Category(c1,"SubFolder1");
					Category sc2= new Category(c1,"SubFolder2");
					Category sc3= new Category(c1,"SubFolder3");

					Category sc4= new Category(c2,"SubFolder4");
					Category sc5= new Category(c2,"SubFolder5");
					Category sc6= new Category(c2,"SubFolder6");

					Entry e1 = new Entry(u.rootCategory,"pass1","name1","TestEntry#1","comment","url",-1);
					Entry e2 = new Entry(c1,"pass1","name2","TestEntry#2","comment","url",-1);
					Entry e3 = new Entry(c1,"pass1","name3","TestEntry#3","comment","url",-1);
					Entry e4 = new Entry(sc1,"pass1","name4","TestEntry#4","comment","url",-1);
		}
}

