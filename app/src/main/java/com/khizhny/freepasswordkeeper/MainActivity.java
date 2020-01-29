package com.khizhny.freepasswordkeeper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import static com.khizhny.freepasswordkeeper.LoginActivity.URL_4PDA_PRIVACY;
import static com.khizhny.freepasswordkeeper.LoginActivity.goToMarket;

public class MainActivity extends AppCompatActivity {

    private static final int PWD_MIN_LENGTH = 10;
    private static final int PWD_MAX_LENGTH = 14;

    private static final int REQUEST_CODE_SIGN_IN_FOR_BACKUP = 9001; // Google sign in and backup
    private static final int REQUEST_CODE_SIGN_IN_FOR_RESTORE = 9002; // Google sign in and restore
    private static final int REQUEST_CODE_OPEN_ITEM = 9004; // File opened from drive
    //Backup paths
    public static final String SD_BACKUP_FOLDER_NAME = "/FreePasswordKeeper";
    public static final String ZIP_ARCHIVE_INNER_FOLDER_NAME = "/FreePasswordKeeper";
    public static final String ZIP_ARCHIVE_FILE_NAME = "pass_backup.zip";
    private static final String LOADED_ZIP_FILENAME = "loaded_file.zip";
    private static final String ZIP_TYPE = "application/zip";


    private User user;
    private DbHelper db;

    private GoogleSignInAccount googleSignInAccount;
    private GoogleSignInClient googleSignInClient; // Client for High order operations with drive
    private DriveServiceHelper driveServiceHelper;

    private Category currentCategory;
    private Category backNode; // unlinked node just to navigate back on tree
    private Queue<Node> selectedNodes = new ConcurrentLinkedQueue<>(); // stores Entry for CopyPasting.

    // UI Views
    private ListView listView;
    private ProgressBar progressBar;
    private Menu menu;

    private AlertDialog alertDialog;
    private boolean exitWarnProtection = true;
    private PasswordGenerator pwGenerator;
    private static boolean editMode; // if true the Entry edition is allowed

    private static final char[] ALPHA_UPPER_CHARACTERS = {'A', 'B', 'C', 'D',
            'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
            'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
    private static final char[] ALPHA_LOWER_CHARACTERS = {'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
    private static final char[] NUMERIC_CHARACTERS = {'0', '1', '2', '3', '4',
            '5', '6', '7', '8', '9'};
    private static final char[] SPECIAL_CHARACTERS = {'~', '`', '!', '@', '#',
            '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+', '[', '{',
            ']', '}', '\\', '|', ';', ':', '\'', '"', ',', '<', '.', '>', '/',
            '?'};

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

        backNode = new Category(new User("system", -1));
        backNode.name = "..";

        Set<PasswordGenerator.PasswordCharacterSet> values = new HashSet<PasswordGenerator.PasswordCharacterSet>(EnumSet.allOf(SummerCharacterSets.class));
        pwGenerator = new PasswordGenerator(values, PWD_MIN_LENGTH, PWD_MAX_LENGTH);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEntryDialog(null);

            }
        });

        listView = findViewById(R.id.list_view);
        db = DbHelper.getInstance(this);
        db.open();

        int user_id = this.getIntent().getIntExtra("user_id", -1);
        String password = this.getIntent().getStringExtra("password");
        String login = this.getIntent().getStringExtra("login");

        user = db.getUser(user_id, true, password, login);
        if (user != null) {
            currentCategory = user.rootCategory;
            if (savedInstanceState != null) {
                int currentCategoryId = savedInstanceState.getInt("current_category_id");
                currentCategory = findCategory(currentCategoryId, user.rootCategory);
            }
            listView.setAdapter(new ListAdapter(getNodes()));
        }

        // Google signIn configuration
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope (DriveScopes.DRIVE_FILE))
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
    }

    public Category findCategory(int id, Category category) {
        if (category.id == id) return category;
        if (!hasCaregoryInChildren(category, id)) {
            return null;
        } else {
            for (Category s : category.categoryList) {
                if (hasCaregoryInChildren(s, id)) return findCategory(id, s);
            }
            return null;
        }
    }

    public boolean hasCaregoryInChildren(Category c, int id) {
        if (c.id == id) return true;
        for (Category s : c.categoryList) {
            if (hasCaregoryInChildren(s, id)) return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_category_id", currentCategory.id);
    }

    /**
     * Start sign in activity.
     */
    private void googleSignIn(int code) {
        showProgress(true);
        // Google signIn configuration
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope (DriveScopes.DRIVE_FILE))
                .requestEmail()
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
        try {
            googleSignInClient.signOut();
        } catch (Exception e) {
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;

        getMenuInflater().inflate(R.menu.menu_main, menu);

        // disabling folder edit option for root folder
        menu.findItem(R.id.action_edit_folder).setEnabled(currentCategory != user.rootCategory);

        switchSelectionMode(false);
        return true;
    }

    private void switchSelectionMode(boolean enabled) {
        menu.clear();
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (enabled) {
            menu.removeItem(R.id.action_edit_user);
            menu.removeItem(R.id.action_edit_folder);
            menu.removeItem(R.id.action_backup);
            menu.removeItem(R.id.action_add_folder);
            menu.removeItem(R.id.action_restore);
            menu.removeItem(R.id.action_rate);
            menu.removeItem(R.id.action_privacy);
            menu.removeItem(R.id.action_logout);
        } else {
            menu.removeItem(R.id.action_selection_paste);
            menu.removeItem(R.id.action_selection_delete);
            menu.removeItem(R.id.action_selection_cancel);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedNodes.clear();
                        refreshTree();
                        switchSelectionMode(false);
                    }
                });
                break;
            case R.id.action_edit_folder:
                if (currentCategory != user.rootCategory) showCategoryDialog(currentCategory);
                break;
            case R.id.action_edit_user:
                showEditUserDialog();
                break;
            case R.id.action_add_folder:
                showCategoryDialog(null);
                return true;
            case R.id.action_backup:
                checkSignInBeforeBackup();
                return true;
            case R.id.action_logout:
                googleSignOut();
                return true;
            case R.id.action_restore:
                checkSignInBeforeRestore();
                return true;
            case android.R.id.home:
                if (currentCategory == user.rootCategory) {
                    if (exitWarnProtection) {
                        Toast.makeText(this, R.string.exit_warn, Toast.LENGTH_SHORT).show();
                        exitWarnProtection = false;
                    } else {
                        finish();
                    }
                } else { // navigating back
                    currentCategory = (Category) currentCategory.parent;
                    refreshTree();
                }
                return true;
            case R.id.action_privacy:
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(URL_4PDA_PRIVACY));
                startActivity(i);
                return true;
            case R.id.action_rate:
                goToMarket(this);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void deleteSelectedNodes() {
        while (!selectedNodes.isEmpty()) {
            Node n = selectedNodes.poll();
            if (n instanceof Entry) {
                db.deleteEntry((Entry) n);
                n = null;
            } else {
                if (n instanceof Category) {
                    db.deleteCategory((Category) n);
                    n = null;
                }
            }
            selectedNodes.remove(n);
        }
        switchSelectionMode(false);
        refreshTree();
    }

    private void pasteNode() {
        while (!selectedNodes.isEmpty()) {
            Node n = selectedNodes.poll();
            n.moveToNewCategory(currentCategory);
            db.addOrEditNode(n, false);
            selectedNodes.remove(n);
        }
        switchSelectionMode(false);
        refreshTree();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (alertDialog != null) {
            if (alertDialog.isShowing()) alertDialog.dismiss();
        }
        db.close();
    }

    private List<Node> getNodes() {
        exitWarnProtection = true;
        List<Node> treeNodes = new ArrayList<>();
        if (currentCategory.parent != null) treeNodes.add(backNode);

        //Sorting by name
        Collections.sort(currentCategory.categoryList);
        Collections.sort(currentCategory.entryList);

        treeNodes.addAll(currentCategory.categoryList);
        treeNodes.addAll(currentCategory.entryList);
        return treeNodes;
    }

    private class ListAdapter extends ArrayAdapter<Node> {

        ListAdapter(List<Node> categoryList) {
            super(MainActivity.this, R.layout.list_row, categoryList);
        }

        @NonNull
        @Override
        public View getView(int position, View rowView, @NonNull ViewGroup parent) {
            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (vi != null) {
                    rowView = vi.inflate(R.layout.list_row, parent, false);
                }
            }

            if (rowView != null) {
                Node node = getItem(position);
                if (node != null) {
                    rowView.setTag(node);
                    TextView entryText = rowView.findViewById(R.id.entry_text);
                    ImageView entryIcon = rowView.findViewById(R.id.entry_icon);
                    entryText.setText(node.name);
                    int icon = 0;
                    if (node instanceof Category) icon = R.drawable.ic_folder;
                    if (node instanceof Entry) icon = R.drawable.ic_key;
                    entryIcon.setImageResource(icon);
                    if (selectedNodes.contains(node)) {
                        entryText.setPaintFlags(entryText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    } else {
                        entryText.setPaintFlags(entryText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    }
                    rowView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Node n = (Node) v.getTag();
                            if (!selectedNodes.contains(n)) {
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
                        }
                    });

                    rowView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            Node n = (Node) v.getTag();
                            if (n != backNode) selectNode(n);
                            return true;
                        }
                    });
                }
            }
            return rowView;
        }

    }

    private void selectNode(Node node) {
        if (selectedNodes.contains(node)) {
            selectedNodes.remove(node);
        } else {
            selectedNodes.add(node);
        }
        switchSelectionMode(!selectedNodes.isEmpty());
        refreshTree();
    }

    private void showEntryDialog(@Nullable final Entry entry) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.dialog_entry);
        editMode = (entry == null);
        builder.setPositiveButton(R.string.save, null); // will owerride later to prevent dialog from closing
        builder.setNegativeButton(R.string.delete, null);
        alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!editMode) {
                            Toast.makeText(MainActivity.this, R.string.unlock_first, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (entry != null) {
                            if (!editMode) {
                                Toast.makeText(MainActivity.this, R.string.unlock_first, Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(MainActivity.this, R.string.unlock_first, Toast.LENGTH_SHORT).show();
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
                        if (entry == null) { // create new
                            db.addOrEditNode(new Entry(currentCategory, password, login, name, comment, url, -1), false);
                        } else { // update existing
                            entry.name = name;
                            entry.comment = comment;
                            entry.password = password;
                            entry.url = url;
                            entry.login = login;
                            db.addOrEditNode(entry, false);
                        }
                        refreshTree();
                        alertDialog.dismiss();
                    }
                });
            }
        });
        alertDialog.show();

        if (entry != null) {
            //noinspection ConstantConditions
            ((EditText) alertDialog.findViewById(R.id.entry_name)).setText(entry.name);
            ((EditText) alertDialog.findViewById(R.id.entry_comment)).setText(entry.comment);
            ((EditText) alertDialog.findViewById(R.id.entry_url)).setText(entry.url);
            ((EditText) alertDialog.findViewById(R.id.entry_login)).setText(entry.login);
            ((EditText) alertDialog.findViewById(R.id.entry_password)).setText(entry.password);
        }

        alertDialog.findViewById(R.id.entry_copy_pass).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //noinspection ConstantConditions
                String password = ((EditText) alertDialog.findViewById(R.id.entry_password)).getText().toString();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(password, password);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, getString(R.string.msg_clipboard), Toast.LENGTH_SHORT).show();
            }
        });

        alertDialog.findViewById(R.id.entry_copy_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //noinspection ConstantConditions
                String login = ((EditText) alertDialog.findViewById(R.id.entry_login)).getText().toString();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(login, login);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, getString(R.string.msg_clipboard), Toast.LENGTH_SHORT).show();
            }
        });

        alertDialog.findViewById(R.id.entry_redirect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //noinspection ConstantConditions
                String url = ((EditText) alertDialog.findViewById(R.id.entry_url)).getText().toString();
                if (android.util.Patterns.WEB_URL.matcher(url).matches()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://" + url;
                    }
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                } else {
                    Toast.makeText(MainActivity.this, "Provided URL is invalid", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageButton generate = alertDialog.findViewById(R.id.entry_generate);
        generate.setVisibility(editMode ? View.VISIBLE : View.GONE);
        generate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String password = Gpw.generate(12); // pronounceable password
                ((EditText) alertDialog.findViewById(R.id.entry_password)).setText(password);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(password, password);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, getString(R.string.long_press_for_pass), Toast.LENGTH_SHORT).show();
            }
        });
        generate.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String password = pwGenerator.generatePassword(); // Strong password
                ((EditText) alertDialog.findViewById(R.id.entry_password)).setText(password);
                return true; // disable onClick listener
            }
        });

        ImageButton lock = (ImageButton) alertDialog.findViewById(R.id.entry_lock);
        lock.setImageResource(editMode ? R.drawable.ic_lock_open : R.drawable.ic_lock_closed);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editMode = !editMode;
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

                        if (category == null) {
                            Category newCategory = new Category(currentCategory, name);
                            db.addOrEditNode(newCategory, false);
                        } else {
                            category.name = name;
                            db.addOrEditNode(category, false);
                        }
                        refreshTree();
                        alertDialog.dismiss();
                    }
                });
        builder.setNegativeButton(R.string.delete,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (category != null) {
                            showMessageOKCancel(getString(R.string.delete_folder) + " " + category.name + "?", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    currentCategory = category.parent;
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
        if (category != null) {
            ((EditText) alertDialog.findViewById(R.id.category_name)).setText(category.name);
        }
    }

    private void refreshTree() {
        listView.setAdapter(new ListAdapter(getNodes()));
        ((ListAdapter) listView.getAdapter()).notifyDataSetChanged();
        MenuItem editFolder = menu.findItem(R.id.action_edit_folder);
        if (editFolder != null) editFolder.setEnabled(currentCategory != user.rootCategory);
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.ok, okListener)
                .setNegativeButton(R.string.cancel, cancelListener)
                .create()
                .show();
    }

    private void backupToGoogleDrive(File zipFile) {
        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        this, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(googleSignInAccount.getAccount());
        com.google.api.services.drive.Drive googleDriveService =
                new com.google.api.services.drive.Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(getString(R.string.app_name))
                        .build();
        driveServiceHelper = new DriveServiceHelper(googleDriveService);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
        String file_name = sdf.format(new Date())+ "_" + zipFile.getName();

        Task uploadTask = driveServiceHelper.uploadFile(file_name, zipFile, ZIP_TYPE);
        uploadTask.addOnCompleteListener(
                new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        showProgress(false);
                    }
                }
        );
        uploadTask.addOnSuccessListener(new OnSuccessListener() {
            @Override
            public void onSuccess(Object o) {
                Toast.makeText(MainActivity.this,
                        "Backup upload successfully", Toast.LENGTH_LONG).show();
            }
        });
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, e.getLocalizedMessage()
                        , Toast.LENGTH_LONG).show();
            }
        });
        uploadTask.addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Toast.makeText(MainActivity.this,
                        "Backup upload cancelled.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkSignInBeforeBackup() {
        showProgress(true);
        if(googleSignInAccount == null){
            googleSignIn(REQUEST_CODE_SIGN_IN_FOR_BACKUP);
        } else {
            backupToGoogleDrive(backupToZipFile());
        }
    }
    private void checkSignInBeforeRestore() {
        showProgress(true);
        if(googleSignInAccount == null){
            googleSignIn(REQUEST_CODE_SIGN_IN_FOR_RESTORE);
        } else {
            sendDriveFilePickerIntent();
        }
    }

    private void sendDriveFilePickerIntent(){
        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        this, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(googleSignInAccount.getAccount());
        com.google.api.services.drive.Drive googleDriveService =
                new com.google.api.services.drive.Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(getString(R.string.app_name))
                        .build();
        driveServiceHelper = new DriveServiceHelper(googleDriveService);
        Intent filePickerIntent = driveServiceHelper.createFilePickerIntent(ZIP_TYPE);
        startActivityForResult(filePickerIntent, REQUEST_CODE_OPEN_ITEM);
    }

    public void replaceAppDatabase(File zipFile) {
        //Unzipping the file
        File workingFolder = zipFile.getParentFile();
        try {
            if (Zip.unZipFile(zipFile, workingFolder)) {
                zipFile.delete();
                // replacing the database
                OutputStream outputStreamtput;
                InputStream inputStream;
                File dbFile = this.getDatabasePath(DbHelper.DATABASE_NAME);
                File newDbFile = new File (workingFolder.getAbsolutePath()+ "/" + DbHelper.DATABASE_NAME);

                //String sdFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + SD_BACKUP_FOLDER_NAME;
                if (workingFolder.exists() & newDbFile.exists()) {
                    try {
                        outputStreamtput = new FileOutputStream(dbFile);
                        inputStream = new FileInputStream(newDbFile);
                        // Transfer bytes from the input file to the output file
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStreamtput.write(buffer, 0, length);
                        }
                        // Close and clear the streams
                        outputStreamtput.flush();
                        outputStreamtput.close();
                        inputStream.close();
                        Toast.makeText(this, R.string.success, Toast.LENGTH_LONG).show();
                        newDbFile.delete();
                        finish(); // going back to Login activity
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
            } else {
                Toast.makeText(MainActivity.this, R.string.unzip_error, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.unzip_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GoogleSignInResult result;
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN_FOR_BACKUP:
                result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                if (result.isSuccess()) {
                    // Google SignIn successful.
                    googleSignInAccount = result.getSignInAccount();
                    backupToGoogleDrive(backupToZipFile());
                } else {
                    // SignIn failed
                    googleSignInAccount = null;
                    Toast.makeText(MainActivity.this, R.string.sign_in_problems, Toast.LENGTH_LONG).show();
                }
                break;

            case REQUEST_CODE_SIGN_IN_FOR_RESTORE:
                result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                if (result.isSuccess()) {
                    // Google SignIn successful.
                    googleSignInAccount = result.getSignInAccount();
                    sendDriveFilePickerIntent();
                } else {
                    // SignIn failed
                    googleSignInAccount = null;
                    Toast.makeText(MainActivity.this, R.string.sign_in_problems, Toast.LENGTH_LONG).show();
                }
                break;

            case REQUEST_CODE_OPEN_ITEM:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        downloadFromGoogleDrive(uri);
                    }
                } else {
                    //mOpenItemTaskSource.setException(new RuntimeException(getString(R.string.unable_to_open_file)));
                    Toast.makeText(MainActivity.this, "Error loading file from drive", Toast.LENGTH_LONG).show();
                }
                showProgress(false);
                break;
        }
        showProgress(false);
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Saves a file from its {@code uri} returned from the Storage Access Framework file picker
     *
     */
    private void downloadFromGoogleDrive(Uri uri) {
        if (driveServiceHelper != null) {
            File destinationFile = new File(this.getFilesDir() + SD_BACKUP_FOLDER_NAME + "/"+ LOADED_ZIP_FILENAME);
            driveServiceHelper.downloadFile(getContentResolver(), uri, destinationFile)
                    .addOnSuccessListener(zipFile -> {
                        replaceAppDatabase(zipFile);
                    })
                    .addOnFailureListener(exception ->
                            Toast.makeText(this, "Unable to open file from picker.",
                                    Toast.LENGTH_LONG).show()
                    );

        }
    }
    public File backupToZipFile() {
        InputStream myInput;
        File dbPath = this.getDatabasePath(DbHelper.DATABASE_NAME);
        File dataFolder = this.getFilesDir();
        String folderForBackupPath = dataFolder.getAbsolutePath() + SD_BACKUP_FOLDER_NAME;
        File folderForBackup = new File(folderForBackupPath);
        if (folderForBackup.exists()){
            folderForBackup.delete();
            folderForBackup.mkdirs();
        }
        folderForBackup.mkdirs();
        if (folderForBackup.exists()) {
            try {
                // Copying BD file to SD card folder
                myInput = new FileInputStream(dbPath);
                // Set the output folder on the Scard
                File directory = new File(folderForBackupPath + ZIP_ARCHIVE_INNER_FOLDER_NAME);
                if (!directory.exists()) directory.mkdirs();
                File dbCopy = new File(directory.getPath() + "//" + DbHelper.DATABASE_NAME);
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
                String src_file_path = folderForBackupPath + ZIP_ARCHIVE_INNER_FOLDER_NAME;
                String destination_location = folderForBackupPath + "/" + ZIP_ARCHIVE_FILE_NAME;
                if (Zip.compressFolder(new File(src_file_path), new File(destination_location))) {
                    dbCopy.delete();
                    directory.delete();
                    return  new File(destination_location);
                }
            } catch (FileNotFoundException e) {
                Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            } catch (IOException e) {
                Toast.makeText(this, R.string.file_io_error, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
        return null;
    }

    private void showEditUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.dialog_new_user);

        builder.setPositiveButton(R.string.save,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //noinspection ConstantConditions
                        String userName = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
                        //noinspection ConstantConditions
                        String userPass = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
                        //noinspection ConstantConditions
                        String userPass2 = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword2)).getText().toString();

                        boolean passOK = false;
                        boolean nameOK = false;

                        if (userPass.length() < MIN_USER_PASS_LENGTH) {
                            Toast.makeText(getApplicationContext(), R.string.short_pass, Toast.LENGTH_SHORT).show();
                        } else {
                            if (userPass2.equals(userPass)) {
                                passOK = true;
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
                            }
                        }

                        if (userName.length() < MIN_USERNAME_LENGTH) {
                            Toast.makeText(getApplicationContext(), R.string.name_is_too_short, Toast.LENGTH_SHORT).show();
                        } else {
                            nameOK = true;
                        }

                        if (passOK && nameOK) {
                            user.name = userName;
                            // reencrypting all user data in db with new encriptor
                            user.decrypter = new Decrypter(userPass, userName);
                            user.name_encrypted = user.decrypter.encrypt(userName);
                            db.addOrEditNode(user, true);
                            alertDialog.dismiss();
                        }
                    }
                });
        alertDialog = builder.create();
        alertDialog.show();
        ((EditText) alertDialog.findViewById(R.id.newUserName)).setText(user.name);
    }
}

