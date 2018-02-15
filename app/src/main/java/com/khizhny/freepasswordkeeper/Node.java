package com.khizhny.freepasswordkeeper;

import android.content.ContentValues;
import android.support.annotation.NonNull;

abstract class Node implements Comparable<Node> {
		int id=-1;			 	// will be auto generated in DB after save
		String name;			// Name of Entry, Category or User
		Node parent=null; // back reference to parent Category. null for root categories

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
}
