package com.khizhny.freepasswordkeeper;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.drive.CreateFileActivityOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.khizhny.freepasswordkeeper.LoginActivity.MIN_USERNAME_LENGTH;
import static com.khizhny.freepasswordkeeper.LoginActivity.MIN_USER_PASS_LENGTH;


@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

		private static final int PWD_MIN_LENGTH = 10;
		private static final int PWD_MAX_LENGTH = 14;
		private static final String TAG = "TAG";

		private static final int DIALOG_ERROR_CODE =100;
		private static final int REQUEST_CODE_ASK_PERMISSIONS=9000; // Read SD card
		private static final int REQUEST_CODE_SIGN_IN_FOR_BACKUP = 9001; // Google sign in and backup
		private static final int REQUEST_CODE_SIGN_IN_FOR_RESTORE = 9002; // Google sign in and restore
		private static final int REQUEST_CODE_CREATOR = 9003; // File created
		private static final int REQUEST_CODE_OPEN_ITEM = 9004; // File opened from drive

		//Backup paths
		public static final String SD_BACKUP_FOLDER_NAME = "/FreePasswordKeeper";
		public static final String ZIP_ARCHIVE_INNER_FOLDER_NAME = "/FreePasswordKeeper";
		public static final String ZIP_ARCHIVE_FILE_NAME = "pass_backup.zip";
		private static final String LOADED_ZIP_FILENAME = "loaded_file.zip";


		private User user;
		private DbHelper db;

		//private GoogleApiClient googleApiClient;

		private GoogleSignInAccount googleSignInAccount;
		private GoogleSignInClient googleSignInClient; // Client for High order operations with drive
		private DriveClient driveClient;
		private DriveResourceClient driveResourceClient;
		private TaskCompletionSource<DriveId> mOpenItemTaskSource;


		private Category currentCategory;
		private Category backNode; // unlinked node just to navigate back on tree
		private Queue<Node> selectedNodes = new ConcurrentLinkedQueue<>(); // stores Entry for CopyPasting.

		// UI Views
		private ListView listView;
		private ProgressBar progressBar;
		private Menu menu;

		private AlertDialog alertDialog;
		private boolean exitWarnProtection =true;
		private PasswordGenerator pwGenerator;
		private static boolean editMode; // if true the Entry edition is allowed

		private static final char[] ALPHA_UPPER_CHARACTERS = { 'A', 'B', 'C', 'D',
						'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
						'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };
		private static final char[] ALPHA_LOWER_CHARACTERS = { 'a', 'b', 'c', 'd',
						'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
						'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };
		private static final char[] NUMERIC_CHARACTERS = { '0', '1', '2', '3', '4',
						'5', '6', '7', '8', '9' };
		private static final char[] SPECIAL_CHARACTERS = { '~', '`', '!', '@', '#',
						'$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+', '[', '{',
						']', '}', '\\', '|', ';', ':', '\'', '"', ',', '<', '.', '>', '/',
						'?' };

		private enum SummerCharacterSets implements PasswordGenerator.PasswordCharacterSet {
				ALPHA_UPPER(ALPHA_UPPER_CHARACTERS, 1),
				ALPHA_LOWER(ALPHA_LOWER_CHARACTERS, 1),
				NUMERIC(NUMERIC_CHARACTERS, 1),
				SPECIAL(SPECIAL_CHARACTERS, 1);

				private final char[] chars;
				private final int minUsage;

				SummerCharacterSets(char[] chars, int minUsage) {
						this.chars = chars;
						this.minUsage = minUsage;
				}

				@Override
				public char[] getCharacters() {
						return chars;
				}

				@Override
				public int getMinCharacters() {
						return minUsage;
				}
		}

		@Override
		protected void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				setContentView(R.layout.activity_main);

				backNode=new Category(new User("system",-1));
				backNode.name="..";

				Set<PasswordGenerator.PasswordCharacterSet> values = new HashSet<PasswordGenerator.PasswordCharacterSet>(EnumSet.allOf(SummerCharacterSets.class));
				pwGenerator = new PasswordGenerator(values, PWD_MIN_LENGTH, PWD_MAX_LENGTH);

				Toolbar toolbar = findViewById(R.id.toolbar);
				setSupportActionBar(toolbar);

				ActionBar actionBar = getSupportActionBar();
				if (actionBar!=null) actionBar.setDisplayHomeAsUpEnabled(true);

				FloatingActionButton fab = findViewById(R.id.fab);
				fab.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
								showEntryDialog(null);

						}
				});

				listView=findViewById(R.id.list_view);
				db=DbHelper.getInstance(this);
				db.open();

				int user_id = this.getIntent().getIntExtra("user_id",-1);
				String password = this.getIntent().getStringExtra("password");
				String login = this.getIntent().getStringExtra("login");

				user=db.getUser(user_id,true,password,login);
				if (user!=null) {
						currentCategory = user.rootCategory;
						if (savedInstanceState!=null){
								int currentCategoryId=savedInstanceState.getInt("current_category_id");
								currentCategory=findCategory(currentCategoryId,user.rootCategory);
						}
						listView.setAdapter(new ListAdapter(getNodes()));
				}

				// Google signIn configuration
				GoogleSignInOptions signInOptions =	new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
								.requestScopes(Drive.SCOPE_FILE)
								.build();

				GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
		}

		public Category findCategory(int id, Category category){
				if (category.id==id) return category;
				if (!hasCaregoryInChildren(category,id)){
						return null;
				}else{
						for (Category s: category.categoryList) {
								if (hasCaregoryInChildren(s,id)) return findCategory(id,s);
						}
						return null;
				}
		}

		public boolean hasCaregoryInChildren(Category c,int id){
				if (c.id==id) return true;
				for (Category s: c.categoryList) {
						if (hasCaregoryInChildren(s,id)) return true;
				}
				return false;
		}

		@Override
		public void onSaveInstanceState(Bundle outState) {
				super.onSaveInstanceState(outState);
				outState.putInt("current_category_id",currentCategory.id);
		}

		/** Start sign in activity. */
		private void googleSignIn(int code) {
				showProgress(true);
				// Google signIn configuration
				GoogleSignInOptions signInOptions =	new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
								.requestScopes(Drive.SCOPE_FILE)
								.build();
				googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
				startActivityForResult(googleSignInClient.getSignInIntent(), code);
		}

		private void showProgress(final boolean show) {
				int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
				progressBar = findViewById(R.id.progressBar2);
				progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
				progressBar.animate().setDuration(shortAnimTime).alpha(
								show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
								progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
						}
				});
		}

		private void googleSignOut() {
				googleSignInClient.signOut();
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
				// Inflate the menu; this adds items to the action bar if it is present.
				getMenuInflater().inflate(R.menu.menu_main, menu);
				this.menu=menu;
				switchSelectionMode(false);
				return true;
		}

		private void switchSelectionMode(boolean enabled){
				menu.clear();
				getMenuInflater().inflate(R.menu.menu_main, menu);
				if (enabled){
						menu.removeItem(R.id.action_edit_user);
						menu.removeItem(R.id.action_edit_folder);
						menu.removeItem(R.id.action_backup);
						menu.removeItem(R.id.action_add_folder);
						menu.removeItem(R.id.action_restore);
				}else{
						menu.removeItem(R.id.action_selection_paste);
						menu.removeItem(R.id.action_selection_delete);
						menu.removeItem(R.id.action_selection_cancel);
				}
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
				switch(item.getItemId()){
						case R.id.action_selection_cancel:
								selectedNodes.clear();
								refreshTree();
								switchSelectionMode(false);
								break;
						case R.id.action_selection_paste:
								pasteNode();
								break;
						case R.id.action_selection_delete:
								showMessageOKCancel(String.format(getString(R.string.delete_selected_items), selectedNodes.size()), new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
												deleteSelectedNodes();
										}
								},new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
												selectedNodes.clear();
												refreshTree();
												switchSelectionMode(false);
										}
								});
								break;
						case R.id.action_edit_folder:
								if (currentCategory!=user.rootCategory)	showCategoryDialog(currentCategory);
								break;
						case R.id.action_edit_user:
								showEditUserDialog();
								break;
						case R.id.action_add_folder:
								showCategoryDialog(null);
								return true;
						case R.id.action_backup:
								requestSdWritePermissions();
								backup();
								return true;
						case R.id.action_restore:
								restore();
								return true;
					case android.R.id.home:
							if (currentCategory==user.rootCategory){
									if (exitWarnProtection) {
											Toast.makeText(this, R.string.exit_warn, Toast.LENGTH_SHORT).show();
											exitWarnProtection=false;
									}else{
											finish();
									}
							}else{ // navigating back
									currentCategory = (Category) currentCategory.parent;
									refreshTree();
							}
							return true;
				}

				return super.onOptionsItemSelected(item);
		}

		private void deleteSelectedNodes() {
				while (!selectedNodes.isEmpty()){
						Node n= selectedNodes.poll();
						if (n instanceof Entry) {
								db.deleteEntry((Entry) n);
								n=null;
						} else {
								if (n instanceof Category) {
										db.deleteCategory((Category) n);
										n=null;
								}
						}
						selectedNodes.remove(n);
				}
				switchSelectionMode(false);
				refreshTree();
		}

		private void pasteNode() {
				while (!selectedNodes.isEmpty()){
						Node n= selectedNodes.poll();
						n.moveToNewCategory(currentCategory);
						db.addOrEditNode(n,false);
						selectedNodes.remove(n);
				}
				switchSelectionMode(false);
				refreshTree();
		}

		@Override
		protected void onStop() {
				super.onStop();
				if (alertDialog!=null) {
						if (alertDialog.isShowing()) alertDialog.dismiss();
				}
				db.close();
		}

		private List<Node> getNodes(){
				exitWarnProtection=true;
				List <Node> treeNodes = new ArrayList<>();
				if (currentCategory.parent!=null) treeNodes.add(backNode);

				//Sorting by name
				Collections.sort(currentCategory.categoryList);
				Collections.sort(currentCategory.entryList);

				treeNodes.addAll(currentCategory.categoryList);
				treeNodes.addAll(currentCategory.entryList);
				return treeNodes;
		}

		private class ListAdapter extends ArrayAdapter<Node> {

				ListAdapter(List<Node> categoryList) {
						super(MainActivity.this,R.layout.list_row, categoryList);
				}

				//Handler for rule picker dialog
				@NonNull
				@Override
				public View getView(int position, View rowView , @NonNull ViewGroup parent) {
						if (rowView == null) {
								LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
								if (vi != null) {
										rowView = vi.inflate(R.layout.list_row, parent, false);
								}
						}

						if (rowView != null) {
								Node node = getItem(position);
								if (node!=null) {
										rowView.setTag(node);
										TextView entryText = rowView.findViewById(R.id.entry_text);
										entryText.setText(node.name);
										int icon = 0;
										if (node instanceof Category) icon = R.drawable.ic_folder;
										if (node instanceof Entry) icon = R.drawable.ic_key;
										entryText.setCompoundDrawablesWithIntrinsicBounds(getDrawable(icon), null, null, null);
										if (selectedNodes.contains(node)) {
												entryText.setPaintFlags(entryText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
										}else{
												entryText.setPaintFlags(entryText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
										}
										rowView.setOnClickListener(new View.OnClickListener() {
												@Override
												public void onClick(View v) {
														Node n = (Node) v.getTag();
														if (n instanceof Category) {
																if (n == backNode) { // navigating back on tree
																		currentCategory = (Category) currentCategory.parent;
																} else {  // navigating forward on tree
																		if (!selectedNodes.contains(n)) {
																				currentCategory = (Category) n;
																		}
																}
																refreshTree();
														}
														if (n instanceof Entry) {
																showEntryDialog((Entry) n);
														}
												}
										});

										rowView.setOnLongClickListener(new View.OnLongClickListener() {
												@Override
												public boolean onLongClick(View v) {
														Node n = (Node) v.getTag();
														if (n != backNode)	selectNode(n);
														return true;
												}
										});
								}

						}
						return rowView;
				}

		}

		private void selectNode(Node node){
				if (selectedNodes.contains(node)) {
						selectedNodes.remove(node);
				}else {
						selectedNodes.add(node);
				}
				switchSelectionMode(!selectedNodes.isEmpty());
				refreshTree();
		}

		private void showEntryDialog(@Nullable final Entry entry) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setView(R.layout.dialog_entry);
				editMode=(entry==null);
				builder.setPositiveButton(R.string.save,null); // will owerride later to prevent dialog from closing
				builder.setNegativeButton(R.string.delete,null);
				alertDialog = builder.create();
				alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
						@Override
						public void onShow(DialogInterface dialog) {
								alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View v) {
												if (!editMode) {
														Toast.makeText(MainActivity.this, R.string.unlock_first,Toast.LENGTH_SHORT).show();
														return;
												}
												if (entry!=null) {
														if (!editMode) {
																Toast.makeText(MainActivity.this, R.string.unlock_first,Toast.LENGTH_SHORT).show();
																return;
														}
														db.deleteEntry(entry);
														refreshTree();
												}
												alertDialog.dismiss();
										}
								});

								alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View v) {
												if (!editMode) {
														Toast.makeText(MainActivity.this, R.string.unlock_first,Toast.LENGTH_SHORT).show();
														return;
												}
												//noinspection ConstantConditions
												String name = ((EditText) alertDialog.findViewById(R.id.entry_name)).getText().toString();
												//noinspection ConstantConditions
												String comment = ((EditText) alertDialog.findViewById(R.id.entry_comment)).getText().toString();
												//noinspection ConstantConditions
												String password = ((EditText) alertDialog.findViewById(R.id.entry_password)).getText().toString();
												//noinspection ConstantConditions
												String url = ((EditText) alertDialog.findViewById(R.id.entry_url)).getText().toString();
												//noinspection ConstantConditions
												String login = ((EditText) alertDialog.findViewById(R.id.entry_login)).getText().toString();
												if (entry==null) { // create new
														db.addOrEditNode(new Entry(currentCategory,password,login,name,comment,url,-1), false);
												}else{ // update existing
														entry.name=name;
														entry.comment=comment;
														entry.password=password;
														entry.url=url;
														entry.login=login;
														db.addOrEditNode(entry, false);
												}
												refreshTree();
												alertDialog.dismiss();
										}
								});
						}
				});
				alertDialog.show();
				if (entry!=null) {
						//noinspection ConstantConditions
						((EditText)alertDialog.findViewById(R.id.entry_name)).setText(entry.name);
						((EditText)alertDialog.findViewById(R.id.entry_comment)).setText(entry.comment);
						((EditText)alertDialog.findViewById(R.id.entry_url)).setText(entry.url);
						((EditText)alertDialog.findViewById(R.id.entry_login)).setText(entry.login);
						((EditText)alertDialog.findViewById(R.id.entry_password)).setText(entry.password);
				}

				alertDialog.findViewById(R.id.entry_copy).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
								//noinspection ConstantConditions
								String password = ((EditText)alertDialog.findViewById(R.id.entry_password)).getText().toString();
								ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
								ClipData clip = ClipData.newPlainText(password, password);
								clipboard.setPrimaryClip(clip);
								Toast.makeText(MainActivity.this, getString(R.string.msg_clipboard),Toast.LENGTH_SHORT).show();
						}
				});

				alertDialog.findViewById(R.id.entry_redirect).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
								//noinspection ConstantConditions
								String url = ((EditText) alertDialog.findViewById(R.id.entry_url)).getText().toString();
								if (android.util.Patterns.WEB_URL.matcher(url).matches()) {
										if (!url.startsWith("http://") && !url.startsWith("https://")) {
												url="http://" + url;
										}
										Intent i = new Intent(Intent.ACTION_VIEW);
										i.setData(Uri.parse(url));
										startActivity(i);
								} else {
										Toast.makeText(MainActivity.this, "Provided URL is invalid",Toast.LENGTH_SHORT).show();
								}
						}
				});

				alertDialog.findViewById(R.id.entry_generate).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
								String password =Gpw.generate(12); // pronounceable password
								((EditText)alertDialog.findViewById(R.id.entry_password)).setText(password);
								ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
								ClipData clip = ClipData.newPlainText(password, password);
								clipboard.setPrimaryClip(clip);
								Toast.makeText(MainActivity.this, getString(R.string.long_press_for_pass),Toast.LENGTH_SHORT).show();
						}
				});

				alertDialog.findViewById(R.id.entry_generate).setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {
								String password= pwGenerator.generatePassword(); // Strong password
								((EditText)alertDialog.findViewById(R.id.entry_password)).setText(password);
								return true; // disable onClick listener
						}
				});

				ImageButton lock = (ImageButton) alertDialog.findViewById(R.id.entry_lock);
				lock.setImageResource(editMode ? R.drawable.ic_lock_open : R.drawable.ic_lock_closed);
				lock.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
								editMode=!editMode;
								((ImageButton) v).setImageResource(editMode ? R.drawable.ic_lock_open : R.drawable.ic_lock_closed);
								alertDialog.findViewById(R.id.entry_generate).setVisibility(editMode ? View.VISIBLE : View.GONE);
						}
				});

		}

		private void showCategoryDialog(@Nullable final Category category) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setView(R.layout.dialog_category);
				builder.setPositiveButton(R.string.save,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												//noinspection ConstantConditions
												String name = ((EditText) ((AlertDialog) dialog).findViewById(R.id.category_name)).getText().toString();

												if (category==null) {
														Category newCategory = new Category(currentCategory, name);
														db.addOrEditNode(newCategory, false);
												}else{
														category.name=name;
														db.addOrEditNode(category, false);
												}
												refreshTree();
												alertDialog.dismiss();
										}
								});
				builder.setNegativeButton(R.string.delete,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												if (category!=null){
														showMessageOKCancel(getString(R.string.delete_folder)+" "+category.name+"?", new DialogInterface.OnClickListener() {
																@Override
																public void onClick(DialogInterface dialog, int which) {
																		currentCategory=category.parent;
																		db.deleteCategory(category);
																		refreshTree();
																}
														}, null);
												}
												alertDialog.dismiss();
										}
								});
				alertDialog = builder.create();
				alertDialog.show();
				if (category!=null){
						((EditText)alertDialog.findViewById(R.id.category_name)).setText(category.name);
				}
		}

		private void refreshTree(){
				listView.setAdapter(new ListAdapter(getNodes()));
				((ListAdapter)listView.getAdapter()).notifyDataSetChanged();
		}
		
		private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
				new AlertDialog.Builder(this)
								.setMessage(message)
								.setPositiveButton("OK", okListener)
								.setNegativeButton("Cancel", cancelListener)
								.create()
								.show();
		}

		private void requestSdWritePermissions() {
				// requsting SD card access permissions
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						boolean hasWritePermission=PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
						if (!hasWritePermission) {
								if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
										showMessageOKCancel("System needs you permission to read SD card.",
														new DialogInterface.OnClickListener() {
																@Override
																public void onClick(DialogInterface dialog, int which) {
																		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
																				requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CODE_ASK_PERMISSIONS);
																		}
																}
														},null);
										return;
								}
								requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CODE_ASK_PERMISSIONS);
						}
				}
		}

		private void backup(){

				boolean hasWritePermission=PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
				if (!hasWritePermission) {
						requestSdWritePermissions();
				}
				hasWritePermission=PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
				if (hasWritePermission) {
						showProgress(true);
						backupToSd();
					 	googleSignIn(REQUEST_CODE_SIGN_IN_FOR_BACKUP);
				}
		}

		private void restart(){
				Intent intent = getIntent();
				finish();
				startActivity(intent);
		}

		/**
		 * Prompts the user to select a ZIP file
		 *
		 * @return Task that resolves with the selected item's ID.
		 */
		protected Task<DriveId> pickZipFile() {
				OpenFileActivityOptions openOptions =
								new OpenFileActivityOptions.Builder()
												.setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, "application/zip"))
												.setActivityTitle(getString(R.string.select_zip))
												.build();
				return pickItem(openOptions);
		}

		/**
		 * Prompts the user to select a folder
		 *
		 * @param openOptions Filter that should be applied to the selection
		 * @return Task that resolves with the selected item's ID.
		 */
		private Task<DriveId> pickItem(OpenFileActivityOptions openOptions) {
				mOpenItemTaskSource = new TaskCompletionSource<>();
				driveClient
								.newOpenFileActivityIntentSender(openOptions)
								.continueWith(new Continuation<IntentSender, Void>() {
										@Override
										public Void then(@NonNull Task<IntentSender> task) throws Exception {
												startIntentSenderForResult(
																task.getResult(), REQUEST_CODE_OPEN_ITEM, null, 0, 0, 0);
												return null;
										}
								});
				return mOpenItemTaskSource.getTask();
		}



		private void restoreFromDrive() {
				// Launch user interface and allow user to select file
				pickZipFile()
								/*.addOnCompleteListener(new OnCompleteListener<DriveId>() {
										@Override
										public void onComplete(@NonNull Task<DriveId> task) {
												Toast.makeText(MainActivity.this, "No file selected", Toast.LENGTH_LONG).show();
										}
								})*/
								.addOnSuccessListener(this,
												new OnSuccessListener<DriveId>() {
														@Override
														public void onSuccess(DriveId driveId) {
																retrieveContents(driveId.asDriveFile());
														}
												})
								.addOnFailureListener(this, new OnFailureListener() {
										@Override
										public void onFailure(@NonNull Exception e) {
												Log.e(TAG, "No file selected", e);
												Toast.makeText(MainActivity.this, R.string.no_file_selected, Toast.LENGTH_LONG).show();
												finish();
										}
								});
		}

		private void retrieveContents(DriveFile file) {
				Task<DriveContents> openFileTask;
				openFileTask=driveResourceClient.openFile(file, DriveFile.MODE_READ_ONLY);
				openFileTask.continueWithTask(new Continuation<DriveContents, Task<Void>>() {
										@Override
										public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {
												DriveContents contents = task.getResult();
														// Saving zipped backup to Cache folder.
														InputStream myInput =contents.getInputStream();
														File tempFile = File.createTempFile(LOADED_ZIP_FILENAME, null, getApplicationContext().getCacheDir());
														OutputStream myOutput = new FileOutputStream(tempFile);
														byte[] buffer = new byte[100024];
														int length;
														while ((length = myInput.read(buffer)) > 0) {
																myOutput.write(buffer, 0, length);
														}
														myOutput.flush();
														myOutput.close();
														myInput.close();
														//Unzip
														try {
																String folderToExtractTo = Environment.getExternalStorageDirectory().getAbsolutePath() + SD_BACKUP_FOLDER_NAME;
																if (Zip.unZipFile(tempFile, new File(folderToExtractTo))) {
																		tempFile.delete();
																		restoreFromSD();
																} else {
																		Toast.makeText(MainActivity.this, R.string.unzip_error, Toast.LENGTH_LONG).show();
																}
														} catch (Exception e) {
																e.printStackTrace();
																Toast.makeText(MainActivity.this, R.string.unzip_error, Toast.LENGTH_LONG).show();
														}
												Task<Void> discardTask = driveResourceClient.discardContents(contents);
												return discardTask;
										}
								})
								.addOnFailureListener(new OnFailureListener() {
										@Override
										public void onFailure(@NonNull Exception e) {
												Log.e(TAG, "Unable to read contents", e);
												Toast.makeText(MainActivity.this, R.string.unable_to_read_content, Toast.LENGTH_LONG).show();
												finish();
										}
								});
		}

		private void restore() {
				googleSignIn(REQUEST_CODE_SIGN_IN_FOR_RESTORE);
		}

		public void restoreFromSD() {
				OutputStream myOutput;
				File dbPath = this.getDatabasePath(DbHelper.DATABASE_NAME);
				String sdFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + SD_BACKUP_FOLDER_NAME;
				File directorys = new File(sdFolderPath);
				if (directorys.exists()) {
						try {
								myOutput = new FileOutputStream(dbPath);
								// Set the folder on the SDcard
								File dbCopy = new File(sdFolderPath+"//"+DbHelper.DATABASE_NAME);
								// Set the input file stream up:
								InputStream myInputs = new FileInputStream(dbCopy);
								// Transfer bytes from the input file to the output file
								byte[] buffer = new byte[1024];
								int length;
								while ((length = myInputs.read(buffer)) > 0) {
										myOutput.write(buffer, 0, length);
								}
								// Close and clear the streams
								myOutput.flush();
								myOutput.close();
								myInputs.close();
								Toast.makeText(this, R.string.success, Toast.LENGTH_LONG).show();
								dbCopy.delete();
								restart();
						} catch (FileNotFoundException e) {
								Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
								e.printStackTrace();
						} catch (IOException e) {
								Toast.makeText(this, R.string.file_io_error, Toast.LENGTH_LONG).show();
								e.printStackTrace();
						}
				} else {
						Toast.makeText(this, R.string.db_not_found, Toast.LENGTH_LONG).show();
				}
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data) {
				super.onActivityResult(requestCode, resultCode, data);
				GoogleSignInResult result;
				switch (requestCode){
						case REQUEST_CODE_SIGN_IN_FOR_BACKUP:
								result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
								if (result.isSuccess()){
										// Google SignIn successful.
										googleSignInAccount = result.getSignInAccount();
										// Use the last signed in account here since it already have a Drive scope.
										driveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));
										// Build a drive resource client.
										driveResourceClient = Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));
										backupToDrive();
								}else{
										// SignIn failed
										googleSignInAccount = null;
										Toast.makeText(MainActivity.this, R.string.sign_in_problems, Toast.LENGTH_LONG).show();
								}
								break;

						case REQUEST_CODE_SIGN_IN_FOR_RESTORE:
								result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
								if (result.isSuccess()){
										// Google SignIn successful.
										googleSignInAccount = result.getSignInAccount();
										// Use the last signed in account here since it already have a Drive scope.
										driveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));
										// Build a drive resource client.
										driveResourceClient = Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));
										restoreFromDrive();
								}else{
										// SignIn failed
										googleSignInAccount = null;
										Toast.makeText(MainActivity.this, R.string.sign_in_problems, Toast.LENGTH_LONG).show();
								}
								break;

						case REQUEST_CODE_OPEN_ITEM:
								if (resultCode == RESULT_OK) {
										DriveId driveId = data.getParcelableExtra(
														OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID);
										mOpenItemTaskSource.setResult(driveId);
								} else {
										mOpenItemTaskSource.setException(new RuntimeException(getString(R.string.unable_to_open_file)));
								}
								showProgress(false);
								break;
				}
				showProgress(false);
				super.onActivityResult(requestCode, resultCode, data);
		}

		public void backupToSd() {
				InputStream myInput;
				File dbPath = this.getDatabasePath(DbHelper.DATABASE_NAME);
				String sdFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + SD_BACKUP_FOLDER_NAME;
				File sdFolder = new File(sdFolderPath);
				if (!sdFolder.exists())	sdFolder.mkdirs();
				if (sdFolder.exists()) {
						try {
								// Copying BD file to SD card folder
								myInput = new FileInputStream(dbPath);
								// Set the output folder on the Scard
								File directory = new File(sdFolderPath + ZIP_ARCHIVE_INNER_FOLDER_NAME);
								if (!directory.exists()) directory.mkdirs();
								File dbCopy = new File(directory.getPath()+"//"+DbHelper.DATABASE_NAME);
								if (dbCopy.exists()) dbCopy.delete();
								dbCopy.createNewFile();
								OutputStream myOutput = new FileOutputStream(dbCopy);
								byte[] buffer = new byte[100024];
								int length;
								while ((length = myInput.read(buffer)) > 0) {
										myOutput.write(buffer, 0, length);
								}
								myOutput.flush();
								myOutput.close();
								myInput.close();

								// compressing
								String src_file_path = Environment.getExternalStorageDirectory()
												.getAbsolutePath() + SD_BACKUP_FOLDER_NAME+ ZIP_ARCHIVE_INNER_FOLDER_NAME;
								String destination_location = sdFolderPath + "//"+ZIP_ARCHIVE_FILE_NAME;
								if (Zip.compressFolder(new File(src_file_path), new File(destination_location))) {
										dbCopy.delete();
										directory.delete();
										Toast.makeText(this, R.string.backup_saved_to_sd, Toast.LENGTH_LONG).show();
								}
						} catch (FileNotFoundException e) {
								Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
								e.printStackTrace();
						} catch (IOException e) {
								Toast.makeText(this, R.string.file_io_error, Toast.LENGTH_LONG).show();
								e.printStackTrace();
						}
				}
		}

		void backupToDrive() {
					final File zipFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
								+ SD_BACKUP_FOLDER_NAME + "//"+ZIP_ARCHIVE_FILE_NAME);
				driveResourceClient
								.createContents()
								.continueWithTask(
												new Continuation<DriveContents, Task<Void>>() {
														@Override
														public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {
																return createFileIntentSender(task.getResult(), zipFile);
														}
												})
								.addOnFailureListener(
												new OnFailureListener() {
														@Override
														public void onFailure(@NonNull Exception e) {
																Toast.makeText(MainActivity.this, R.string.failed_to_create_new_contents_on_drive, Toast.LENGTH_LONG).show();
														}
												});
		}

		private Task<Void> createFileIntentSender(DriveContents driveContents, File zipFile) {
				// Get an output stream for the contents.
				try {
						OutputStream outputStream = driveContents.getOutputStream();
						InputStream inputStream = new FileInputStream(zipFile);

						// Transfer bytes from the input file to the output file
						byte[] buffer = new byte[100024];
						int length;
						while ((length = inputStream.read(buffer)) > 0) {
								outputStream.write(buffer, 0, length);
						}
						// Close and clear the streams
						outputStream.flush();
						outputStream.close();
						outputStream.close();
				} catch (FileNotFoundException e) {
						e.printStackTrace();
						Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
						return null;
				} catch (IOException e) {
						Toast.makeText(this,R.string.file_io_error, Toast.LENGTH_LONG).show();
						e.printStackTrace();
				}

				// Create the initial metadata - MIME type and title.
				// Note that the user will be able to change the title later.
				Date currentTime = Calendar.getInstance().getTime();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
				String currentDateandTime = sdf.format(new Date());

				MetadataChangeSet metadataChangeSet =
								new MetadataChangeSet.Builder()
												.setMimeType("application/zip")
												.setTitle(currentDateandTime+"_"+ZIP_ARCHIVE_FILE_NAME)
												.build();
				// Set up options to configure and display the create file activity.
				CreateFileActivityOptions createFileActivityOptions =
								new CreateFileActivityOptions.Builder()
												.setInitialMetadata(metadataChangeSet)
												.setInitialDriveContents(driveContents)
												.build();

				return driveClient
								.newCreateFileActivityIntentSender(createFileActivityOptions)
								.continueWith(
												new Continuation<IntentSender, Void>() {
														@Override
														public Void then(@NonNull Task<IntentSender> task) throws Exception {
																startIntentSenderForResult(task.getResult(), REQUEST_CODE_CREATOR, null, 0, 0, 0);
																return null;
														}
												});
		}

		private void showEditUserDialog() {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setView(R.layout.dialog_new_user);

				builder.setPositiveButton(R.string.save,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												//noinspection ConstantConditions
												String userName=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
												//noinspection ConstantConditions
												String userPass=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
												//noinspection ConstantConditions
												String userPass2=((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword2)).getText().toString();

												boolean passOK=false;
												boolean nameOK=false;

												if (userPass.length() < MIN_USER_PASS_LENGTH) {
														Toast.makeText(getApplicationContext(), R.string.short_pass, Toast.LENGTH_SHORT).show();
												}else {
														if (userPass2.equals(userPass)) {
																passOK = true;
														} else {
																Toast.makeText(getApplicationContext(), R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
														}
												}

												if (userName.length() < MIN_USERNAME_LENGTH) {
														Toast.makeText(getApplicationContext(), R.string.name_is_too_short, Toast.LENGTH_SHORT).show();
												}else{
														nameOK = true;
												}

												if (passOK && nameOK) {
														user.name=userName;
														// reencrypting all user data in db with new encriptor
														user.decrypter = new Decrypter(userPass, userName);
														user.name_encrypted=user.decrypter.encrypt(userName);
														db.addOrEditNode(user, true);
														alertDialog.dismiss();
												}
										}
								});
				alertDialog=builder.create();
				alertDialog.show();
				((EditText) alertDialog.findViewById(R.id.newUserName)).setText(user.name);
		}
}
