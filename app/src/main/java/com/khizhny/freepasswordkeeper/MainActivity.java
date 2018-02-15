package com.khizhny.freepasswordkeeper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

		private static final int PWD_MIN_LENGTH = 10;
		private static final int PWD_MAX_LENGTH = 14;
		private User user;
		private DbHelper db;

		private Category currentCategory;
		private Category backNode; // unlinked node just to navigate back on tree

		// UI Views
		private ListView listView;

		private AlertDialog alertDialog;
		private boolean exitWarnProtection =true;
		private PasswordGenerator pwGenerator;

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
						listView.setAdapter(new ListAdapter(getNodes()));
				}
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
				// Inflate the menu; this adds items to the action bar if it is present.
				getMenuInflater().inflate(R.menu.menu_main, menu);
				return true;
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
				switch(item.getItemId()){
					case R.id.action_add_folder:
							showCategoryDialog(null);
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
										rowView.setOnClickListener(new View.OnClickListener() {
												@Override
												public void onClick(View v) {
														Node n = (Node) v.getTag();
														if (n instanceof Category) {
																if (n == backNode) { // navigating back on tree
																		currentCategory = (Category) currentCategory.parent;
																} else {  // navigating forward on tree
																		currentCategory = (Category) n;
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
														if (n instanceof Category) {
																showCategoryDialog((Category) n);
														}
														if (n instanceof Entry) {
																showEntryDialog((Entry) n);
														}
														return false;
												}
										});
								}

						}
						return rowView;
				}

		}

		private void showEntryDialog(@Nullable final Entry entry) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setView(R.layout.dialog_entry);
				builder.setPositiveButton(R.string.save,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												//noinspection ConstantConditions
												String name = ((EditText) ((AlertDialog) dialog).findViewById(R.id.entry_name)).getText().toString();
												//noinspection ConstantConditions
												String comment = ((EditText) ((AlertDialog) dialog).findViewById(R.id.entry_comment)).getText().toString();
												//noinspection ConstantConditions
												String password = ((EditText) ((AlertDialog) dialog).findViewById(R.id.entry_password)).getText().toString();
												//noinspection ConstantConditions
												String url = ((EditText) ((AlertDialog) dialog).findViewById(R.id.entry_url)).getText().toString();
												//noinspection ConstantConditions
												String login = ((EditText) ((AlertDialog) dialog).findViewById(R.id.entry_login)).getText().toString();
												if (entry==null) {
														db.addOrEditNode(new Entry(currentCategory,password,login,name,comment,url,-1), false);
												}else{
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
				builder.setNegativeButton(R.string.delete,
								new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
												if (entry!=null) {
														db.deleteEntry(entry);
														refreshTree();
												}
												alertDialog.dismiss();
										}
								});
				alertDialog = builder.create();
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
														db.deleteCategory(category);
														refreshTree();
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

}
