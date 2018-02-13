package com.khizhny.freepasswordkeeper;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

		private User user;
		private DbHelper db;

		private Category currentCategory;

		// UI Views
		private ListView listView;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				setContentView(R.layout.activity_main);
				Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
				setSupportActionBar(toolbar);

				FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
				fab.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
								Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
												.setAction("Action", null).show();
						}
				});

				listView=findViewById(R.id.list_view);
				db=DbHelper.getInstance(this);
				db.open();

				int user_id = this.getIntent().getIntExtra("user_id",-1);
				String password = this.getIntent().getStringExtra("password");

				user=db.getUser(user_id);
				user.decrypter=new Decrypter(password,user.name);
				currentCategory=user.rootCategory;
				listView.setAdapter(new ListAdapter(getNodes()));
		}

		@Override
		protected void onStop() {
				super.onStop();
				db.close();
		}

		@Override
		protected void onResume() {
				super.onResume();
		}

		private List<Node> getNodes(){
				List <Node> treeNodes = new ArrayList<>();
				treeNodes.addAll(currentCategory.categoryList);
				treeNodes.addAll(currentCategory.entryList);
				return treeNodes;
		}

		private class ListAdapter extends ArrayAdapter<Node> {

				ListAdapter(List<Node> categoryList) {
						super(MainActivity.this,R.layout.list_row, categoryList);
				}

				//Handler for rule picker dialog
				@Override
				public View getView(int position, View rowView , ViewGroup parent) {
						if (rowView == null) {
								LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
								if (vi != null) {
										rowView = vi.inflate(R.layout.list_row, parent, false);
								}
						}

						if (rowView != null) {
								TextView entryText = rowView.findViewById(R.id.entry_text);

								Object o = getItem(position);
								rowView.setTag(o);

								if (o.getClass().equals("Category")){
										entryText.setText(((Category)o).name);
										entryText.setCompoundDrawables(getDrawable(R.drawable.ic_folder),null,null,null);
								}else{
										entryText.setText(((Entry)o).name);
										entryText.setCompoundDrawables(getDrawable(R.drawable.ic_key),null,null,null);
								}


								// switching rule
								entryText.setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View v) {
												//TODO open folder or entry
										}
								});

						}
						//noinspection ConstantConditions
						return rowView;
				}

		}
}
