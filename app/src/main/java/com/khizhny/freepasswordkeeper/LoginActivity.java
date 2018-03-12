package com.khizhny.freepasswordkeeper;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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
		static final int MIN_USERNAME_LENGTH = 1;
		static final int MIN_USER_PASS_LENGTH = 4;
		public static final String URL_4PDA_PRIVACY = "http://4pda.ru/forum/index.php?showtopic=730676&st=20#entry58120636";


		// UI references.
		private Spinner usersView;
		private EditText passwordView;
		private DbHelper db;
		private AlertDialog alertDialog;

		@Override
		protected void onResume() {
				super.onResume();
				db=DbHelper.getInstance(this);
				db.open();
				refreshUserList();
		}

		@Override
		protected void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				setContentView(R.layout.activity_login);
				// Set up the login form.
				usersView = findViewById(R.id.login);

				setTitle(getString(R.string.login_activity_title));
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
				if (savedInstanceState!=null) {
						passwordView.setText(savedInstanceState.getString("user_pass"));
						usersView.setSelection(savedInstanceState.getInt("user_index"));
				}
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
								passwordView.setText(""); // removing correct password
								startActivity(intent);
						} else{
								Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_LONG).show();
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
				builder.setView(R.layout.dialog_new_user);
				builder.setPositiveButton(R.string.create,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												//noinspection ConstantConditions
												String userName=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
												//noinspection ConstantConditions
												String userPass=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
												//noinspection ConstantConditions
												String userPass2=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword2)).getText().toString();
												if (userName.length() < MIN_USERNAME_LENGTH) {
														Toast.makeText(getApplicationContext(), R.string.name_is_too_short, Toast.LENGTH_SHORT).show();
														return;
												}
												if (userPass.length() < MIN_USER_PASS_LENGTH) {
														Toast.makeText(getApplicationContext(), R.string.short_pass, Toast.LENGTH_SHORT).show();
														return;
												}
												if (userPass2.equals(userPass)){
																alertDialog.dismiss();
																User user=new User(userName,-1);
																user.decrypter=new Decrypter(userPass,userName);
																user.name_encrypted=user.decrypter.encrypt(userName);
																addDefaultRecords(user);
																db.addOrEditNode(user,true);
																refreshUserList();
																attemptLogin(user,userPass);
												}else{
														Toast.makeText(getApplicationContext(), R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
												}
										}
								});
				alertDialog=builder.create();
				alertDialog.show();
		}

		private void addDefaultRecords(User u){
					new Category(u.rootCategory,getString(R.string.default_folder_forums));
					new Category(u.rootCategory,getString(R.string.default_folder_credits));
					new Category(u.rootCategory,getString(R.string.default_folder_emails));
					new Category(u.rootCategory,getString(R.string.default_folder_shops));
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
				// Inflate the menu; this adds items to the action bar if it is present.
				getMenuInflater().inflate(R.menu.menu_login, menu);
				return true;
		}

		static void goToMarket(Context ctx){
				Uri uri = Uri.parse("market://details?id=" + ctx.getPackageName());
				Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
				// To count with Play market backstack, After pressing back button,
				// to taken back to our application, we need to add following flags to intent.
				goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
				try {
						ctx.startActivity(goToMarket);
				} catch (ActivityNotFoundException e) {
						ctx.startActivity(new Intent(Intent.ACTION_VIEW,
										Uri.parse("http://play.google.com/store/apps/details?id=" + ctx.getPackageName())));
				}
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
				switch(item.getItemId()){
						case R.id.action_privacy:
								Intent i = new Intent(Intent.ACTION_VIEW);
								i.setData(Uri.parse(URL_4PDA_PRIVACY));
								startActivity(i);
									return true;
						case R.id.action_rate:
								goToMarket(this);
								break;
						case R.id.action_add_user:
								showNewUserDialog();
								return true;
						case R.id.action_delete_user:
								User u = (User) usersView.getSelectedItem();
								if (u!=null) {
										showMessageOKCancel(getString(R.string.are_you_sure), new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
														db.deleteUserInfo(u.id);
														refreshUserList();
												}
										}, null);
								}
								break;
				}

				return super.onOptionsItemSelected(item);
		}

		private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
				new AlertDialog.Builder(this)
								.setMessage(message)
								.setPositiveButton(R.string.ok, okListener)
								.setNegativeButton(R.string.cancel, cancelListener)
								.create()
								.show();
		}

}

