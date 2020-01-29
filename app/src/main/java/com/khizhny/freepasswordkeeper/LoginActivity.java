package com.khizhny.freepasswordkeeper;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class LoginActivity extends AppCompatActivity {

    public static final String LOG = "PASS_KEEPER";
    static final int MIN_USERNAME_LENGTH = 1;
    static final int MIN_USER_PASS_LENGTH = 4;
    public static final String URL_4PDA_PRIVACY = "http://4pda.ru/forum/index.php?showtopic=730676&st=20#entry58120636";
    private static final String KEY_USE_FINGERPRINT = "KEY_USE_FINGERPRINT";
    static final String TAG = MainActivity.class.getSimpleName();
    private static final String FINGERPRINT_KEY_NAME = "FreePassKeeper_SecretKey003";
    private static final String KEY_USER_PASS = "USER_PASS_";

    // Fingerprint auth stuff
    private static boolean useFingerprint = false;
    private static boolean fingerprintSupported;
    private KeyStore keyStore;
    private SecretKey secretKey;
    private FingerprintManager.CryptoObject cryptoObject;
    private FingerprintUiHelper fingerprintUiHelper;
    private Cipher cipher;

    // UI references.
    private Spinner usersView;
    private EditText passwordView;
    private DbHelper db;
    private AlertDialog alertDialog;
    private MenuItem useFingerprintMenuItem;

    @Override
    protected void onResume() {
        super.onResume();
        db = DbHelper.getInstance(this);
        db.open();
        refreshUserList();
        if (fingerprintSupported) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            useFingerprint = settings.getBoolean(KEY_USE_FINGERPRINT, false);
            switchFingerprintAuth(useFingerprint);
        } else {
            switchFingerprintAuth(false);
        }
    }

    private void switchFingerprintAuth(boolean enabled) {
        Log.d(TAG, "switchFingerprintAuth(" + enabled + ")");
        if (enabled) {
            useFingerprint = true;
            findViewById(R.id.fingerprint_image).setVisibility(View.VISIBLE);
            findViewById(R.id.fingerprint_status).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.fingerprint_status)).setText(R.string.fingerprint_hint);
            if (useFingerprintMenuItem != null) useFingerprintMenuItem.setChecked(useFingerprint);
            if (fingerprintUiHelper != null) fingerprintUiHelper.startListening(cryptoObject);
        } else {
            useFingerprint = false;
            findViewById(R.id.fingerprint_image).setVisibility(View.GONE);
            findViewById(R.id.fingerprint_status).setVisibility(View.GONE);
            if (useFingerprintMenuItem != null) useFingerprintMenuItem.setChecked(useFingerprint);
            if (fingerprintUiHelper != null) fingerprintUiHelper.stopListening();
        }
        // Saving preference
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.edit().putBoolean(KEY_USE_FINGERPRINT, useFingerprint).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(false);
        setTitle(getString(R.string.login_activity_title));

        // Set up the login form.
        usersView = findViewById(R.id.login);

        passwordView = findViewById(R.id.password);
        passwordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin((User) usersView.getSelectedItem(), passwordView.getText().toString());
                    return true;
                }
                return false;
            }
        });
        if (savedInstanceState != null) {
            passwordView.setText(savedInstanceState.getString("user_pass"));
            usersView.setSelection(savedInstanceState.getInt("user_index"));
        }

        // enabling fingerprint scanner
        fingerprintSupported = initFingerprintStuff();
        Log.d(TAG, "onCreate  fingerprintSupported =" + fingerprintSupported);
    }

    private boolean initFingerprintStuff() {
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return false;
        try {
            // Check permissions
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.USE_BIOMETRIC) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "No fingerprint permissions granted.", Toast.LENGTH_LONG).show();
                return false;
            }

            KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
            FingerprintManager fingerprintManager = getSystemService(FingerprintManager.class);

            //Check whether the device has a fingerprint sensor//
            if (!fingerprintManager.isHardwareDetected()) {
                return false;
            }

            //Check that the lockscreen is secured//
            if (!keyguardManager.isKeyguardSecure()) {
                // Show a message that the user hasn't set up a fingerprint or lock screen.
                Toast.makeText(this, "Secure lock screen hasn't set up.\n"
                                + "Go to 'Settings -> Security -> Fingerprint' to set up a fingerprint",
                        Toast.LENGTH_LONG).show();
                return false;
            }

            // Check if fingerprints are registered.
            if (!fingerprintManager.hasEnrolledFingerprints()) {
                Toast.makeText(this,
                        "Go to 'Settings -> Security -> Fingerprint' and register at least one" +
                                " fingerprint",
                        Toast.LENGTH_LONG).show();
                return false;
            }

            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            // creating key if not exist
            if (!keyStore.isKeyEntry(FINGERPRINT_KEY_NAME)) {
                createKey(FINGERPRINT_KEY_NAME);
            }

            secretKey = (SecretKey) keyStore.getKey(FINGERPRINT_KEY_NAME, null);

            // Init Cipher object
            if (initCipher()) {
                cryptoObject = new FingerprintManager.CryptoObject(cipher);
                fingerprintUiHelper = new FingerprintUiHelper(getSystemService(FingerprintManager.class),
                        (ImageView) findViewById(R.id.fingerprint_image),
                        (TextView) findViewById(R.id.fingerprint_status), new FingerprintUiHelper.Callback() {
                    @Override
                    public void onAuthenticated() {
                        fingerprintUiHelper.stopListening();
                        attemptLoginWithFingerprint((User) usersView.getSelectedItem());
                    }

                    @Override
                    public void onError() {
                        Toast.makeText(LoginActivity.this, "Fingerprint Error. Try password authentication instead.", Toast.LENGTH_LONG).show();
                    }
                });
                return true;
            } else {
                Toast.makeText(this, "Cipher init failed.", Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    //Create a new method that we’ll use to initialize our cipher//
    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean initCipher() {
        try {
            //Obtain a cipher instance and configure it with the properties required for fingerprint authentication//
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(FINGERPRINT_KEY_NAME,
                    null);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(Decrypter.IV));
            //Return true if the cipher has been initialized successfully//
            return true;
        } catch (KeyStoreException
                | CertificateException
                | UnrecoverableKeyException
                | IOException
                | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | InvalidKeyException e) {
            Log.e(TAG, "Failed to init Cipher:" + e.getMessage());
            return false;
        }
    }

    private void createKey(String keyName) {
        try {
            keyStore.load(null);
            KeyGenParameterSpec.Builder builder;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                builder = new KeyGenParameterSpec.Builder(keyName,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        // Require the user to authenticate with a fingerprint to authorize every use
                        .setUserAuthenticationRequired(true)
                        .setRandomizedEncryptionRequired(false)
                        .setUserAuthenticationValidityDurationSeconds(8 * 60 * 60)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

                // This is a workaround to avoid crashes on devices whose API level is < 24
                // because KeyGenParameterSpec.Builder#setInvalidatedByBiometricEnrollment is only
                // visible on API level +24.
                // Ideally there should be a compat library for KeyGenParameterSpec.Builder but
                // which isn't available yet.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setInvalidatedByBiometricEnrollment(true);
                }
                KeyGenerator mKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                mKeyGenerator.init(builder.build());
                mKeyGenerator.generateKey();
                Log.d(TAG, "New SecretKeyCreated.");
            }
        } catch (NoSuchAlgorithmException
                | InvalidAlgorithmParameterException
                | NoSuchProviderException
                | CertificateException
                | IOException e) {
            Log.e(TAG, "SecretKeyCreationError:" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putInt("user_index", usersView.getSelectedItemPosition());
        outState.putString("user_pass", passwordView.getText().toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (alertDialog != null) {
            if (alertDialog.isShowing()) alertDialog.dismiss();
        }
        db.close();
        if (fingerprintUiHelper != null) fingerprintUiHelper.stopListening();
    }

    private void attemptLogin(User user, String pass) {
        if (user != null) {
            user.decrypter = new Decrypter(pass, user.name);
            if (user.checkPassword()) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("user_id", user.id);
                intent.putExtra("password", pass);
                intent.putExtra("login", user.name);
                passwordView.setText(""); // removing correct password
                if (useFingerprint) encryptAndSavePassword(user, pass);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void attemptLoginWithFingerprint(User user) {
        if (user != null) {
            // Restoring preferences
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String encryptedPassword = settings.getString(KEY_USER_PASS + user.name, "");
            if (encryptedPassword.equals("")) {
                Toast.makeText(this, "Enter Password!", Toast.LENGTH_LONG).show();
            } else {
                Decrypter d = new Decrypter(secretKey);
                String pass = d.decrypt(encryptedPassword);

                attemptLogin(user, pass);
            }

        } else {
            Toast.makeText(this, "Select the user first!", Toast.LENGTH_LONG).show();
        }
    }

    private void encryptAndSavePassword(User user, String pass) {
        if (secretKey != null) {
            Decrypter d = new Decrypter(secretKey);
            String encryptedPassword = d.encrypt(pass);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            settings.edit()
                    .putString(KEY_USER_PASS + user.name, encryptedPassword)
                    .apply();
        }
    }

    private void refreshUserList() {
        List<User> usersList = db.getAllUsers();
        //noinspection unchecked
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, usersList);
        usersView.setAdapter(arrayAdapter);
        if (usersList.size() == 0) showNewUserDialog();
    }


    private void showNewUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.dialog_new_user);
        builder.setPositiveButton(R.string.create,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //noinspection ConstantConditions
                        String userName = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserName)).getText().toString();
                        //noinspection ConstantConditions
                        String userPass = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword)).getText().toString();
                        //noinspection ConstantConditions
                        String userPass2 = ((EditText) ((AlertDialog) dialog).findViewById(R.id.newUserPassword2)).getText().toString();
                        if (userName.length() < MIN_USERNAME_LENGTH) {
                            Toast.makeText(getApplicationContext(), R.string.name_is_too_short, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (userPass.length() < MIN_USER_PASS_LENGTH) {
                            Toast.makeText(getApplicationContext(), R.string.short_pass, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (userPass2.equals(userPass)) {
                            alertDialog.dismiss();
                            User user = new User(userName, -1);
                            user.decrypter = new Decrypter(userPass, userName);
                            user.name_encrypted = user.decrypter.encrypt(userName);
                            addDefaultRecords(user);
                            db.addOrEditNode(user, true);
                            refreshUserList();
                            attemptLogin(user, userPass);
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        alertDialog = builder.create();
        alertDialog.show();
    }

    private void addDefaultRecords(User u) {
        new Category(u.rootCategory, getString(R.string.default_folder_forums));
        new Category(u.rootCategory, getString(R.string.default_folder_credits));
        new Category(u.rootCategory, getString(R.string.default_folder_emails));
        new Category(u.rootCategory, getString(R.string.default_folder_shops));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        useFingerprintMenuItem = menu.findItem(R.id.action_use_fingerprint);
        if (fingerprintSupported) {
            useFingerprintMenuItem.setChecked(useFingerprint);
            useFingerprintMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    item.setChecked(!item.isChecked());
                    useFingerprint = item.isChecked();
                    switchFingerprintAuth(useFingerprint);
                    if (useFingerprint) showMessageOKCancel("Don't forget your password!!! \n" +
                            "It still be needed if you change the device or reinstall the application.", null, null);
                    return false;
                }
            });
        } else {
            menu.removeItem(R.id.action_use_fingerprint);
        }
        return true;
    }

    static void goToMarket(Context ctx) {
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
        switch (item.getItemId()) {
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
                final User u = (User) usersView.getSelectedItem();
                if (u != null) {
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

    @SuppressWarnings("SameParameterValue")
    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(message);
        b.setPositiveButton(R.string.ok, okListener);
        if (cancelListener != null) b.setNegativeButton(R.string.cancel, cancelListener);
        b.create().show();
    }

}

