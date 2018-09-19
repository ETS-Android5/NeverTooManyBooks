/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.taskqueue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Class to wrap events that can not be deseriialized so that the EventsCursor *always* returns a valid Event.
 * 
 * @author Philip Warner
 */
public abstract class LegacyEvent extends Event {

	private static final long serialVersionUID = -8518718598973561219L;
	private static final int TEXT_FIELD_1 = 1;
	private static final int TEXT_FIELD_2 = 2;
	private final byte[] m_original;

	public LegacyEvent(byte[] original, String description) {
		super(description);
		m_original = original;
	}

	@Override
	public View newListItemView(LayoutInflater inflater, Context context, BindableItemSQLiteCursor cursor, ViewGroup parent)
	{
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(ViewGroup.MarginLayoutParams.MATCH_PARENT, ViewGroup.MarginLayoutParams.WRAP_CONTENT);
		TextView tv = new TextView(context);
		tv.setId(TEXT_FIELD_1);
		root.addView(tv, margins);
		tv = new TextView(context);
		tv.setId(TEXT_FIELD_2);
		root.addView(tv, margins);
		return root;
	}

	@Override
	public boolean bindView(View view, Context context, BindableItemSQLiteCursor cursor, Object appInfo) {
		((TextView)view.findViewById(TEXT_FIELD_1)).setText("Legacy Event Placeholder for Event #" + this.getId());
		((TextView)view.findViewById(TEXT_FIELD_2)).setText("This event is obsolete and can not be recovered. It is probably advisable to delete it.");
		return true;
	}

	public byte[] getOriginal() {
		return m_original;
	}

	@Override
	public abstract void addContextMenuItems(final Context ctx, AdapterView<?> parent, final View v, final int position, final long id, ArrayList<ContextDialogItem> items, Object appInfo);
}
