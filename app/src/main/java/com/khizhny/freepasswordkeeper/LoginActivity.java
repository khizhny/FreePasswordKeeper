package com.khizhny.freepasswordkeeper;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class LoginActivity extends AppCompatActivity{

		public static final String LOG = "PASS_KEEPER";
		private static final int MIN_USERNAME_LENGTH = 1;
		private static final int MIN_USER_PASS_LENGTH = 4;

		// UI references.
		private Spinner usersView;
		private EditText passwordView;
		private DbHelper db;
		private AlertDialog alertDialog;

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
				refreshUserList();
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
						user.decrypter=new Decrypter(pass,user.name);
						if (user.checkPassword()) {
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
				List<User> usersList = db.getAllUsers();
					//noinspection unchecked
					ArrayAdapter arrayAdapter =new  ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item, usersList);
				usersView.setAdapter(arrayAdapter);
				if (usersList.size()==0) showNewUserDialog();
		}



		private void showNewUserDialog() {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(R.string.new_user);
				builder.setView(R.layout.dialog_new_user);
				builder.setPositiveButton("Save",
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												//noinspection ConstantConditions
												String userName=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
												//noinspection ConstantConditions
												String userPass=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
												//noinspection ConstantConditions
												String userPass2=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword2)).getText().toString();
												if (userName.length() < MIN_USERNAME_LENGTH) {
														Toast.makeText(getApplicationContext(), "Too short name", Toast.LENGTH_SHORT).show();
														return;
												}
												if (userPass.length() < MIN_USER_PASS_LENGTH) {
														Toast.makeText(getApplicationContext(), "Password too short", Toast.LENGTH_SHORT).show();
														return;
												}
												if (userPass2.equals(userPass)){
																alertDialog.dismiss();
																User user=new User(userName,-1);
																user.decrypter=new Decrypter(userPass,userName);
																user.name_encrypted=user.decrypter.encrypt(user.name);
																addDefaultRecords(user);
																db.addOrEditNode(user,true);
																refreshUserList();
																attemptLogin(user,userPass);
														}else{
														Toast.makeText(getApplicationContext(), "Passwords don't match.", Toast.LENGTH_SHORT).show();
												}
										}
								});
				alertDialog=builder.create();
				alertDialog.show();
		}

		private void addDefaultRecords(User u){
					new Category(u.rootCategory,"Forums");
					new Category(u.rootCategory,"Credit cards");
					new Category(u.rootCategory,"Mailboxes");
					new Category(u.rootCategory,"Online shops");
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
				// Inflate the menu; this adds items to the action bar if it is present.
				getMenuInflater().inflate(R.menu.menu_login, menu);
				return true;
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
				switch(item.getItemId()){
						case R.id.action_add_user:
								showNewUserDialog();
								return true;
						case R.id.action_delete_user:
								User u = (User) usersView.getSelectedItem();
								if (u!=null) {
										db.deleteUserInfo(u.id);
										refreshUserList();
								}
								break;
				}

				return super.onOptionsItemSelected(item);
		}
}

