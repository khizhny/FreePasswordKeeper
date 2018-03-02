package com.khizhny.freepasswordkeeper;

import android.content.ContentValues;
import android.support.annotation.NonNull;

@SuppressWarnings("SuspiciousMethodCalls")
abstract class Node implements Comparable<Node> {
		int id=-1;			 	// will be auto generated in DB after save
		String name;			// Name of Entry, Category or User
		Category parent=null; // back reference to parent Category. null for root categories

		abstract public ContentValues getContentValues();

		@Override
		public String toString() {
				return name;
		}

		@Override
		public int compareTo(@NonNull Node o) {
				try{
						return name.toLowerCase().compareTo(o.name.toLowerCase());
				} catch (Exception e)  {
						return 0;
				}
		}

		public void moveToNewCategory(Category newCategory){
				if (this instanceof Entry){
						parent.entryList.remove(this);
						this.parent=newCategory;
						parent.entryList.add((Entry)this);
				}
				if (this instanceof Category){
						parent.categoryList.remove(this);
						this.parent=newCategory;
						parent.categoryList.add((Category)this);
				}
		}
}
