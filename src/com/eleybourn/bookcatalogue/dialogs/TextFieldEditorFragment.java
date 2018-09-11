/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.UniqueId;

/**
 * Fragment wrapper for the PartialDatePicker dialog
 * 
 * @author pjw
 */
public class TextFieldEditorFragment extends DialogFragment {
	private static final String BKEY_TITLE = "title";
	private static final String BKEY_TEXT = "text";
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnTextFieldEditorListener {
		void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText);
		void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 * @param text		Text to edit
	 *
	 * @return			Created fragment
	 */
	public static TextFieldEditorFragment newInstance(int dialogId, int titleId, String text) {
    	TextFieldEditorFragment frag = new TextFieldEditorFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_TEXT, text);
        args.putInt(BKEY_TITLE, titleId);
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Context a) {
		super.onAttach(a);

		if (! (a instanceof OnTextFieldEditorListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnTextFieldEditorListener");
		
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        int title = getArguments().getInt(BKEY_TITLE);
        String text = getArguments().getString(BKEY_TEXT);

        TextFieldEditor editor = new TextFieldEditor(getActivity());
        editor.setText(text);
        editor.setTitle(title);
        editor.setOnEditListener(mEditListener);
        return editor;
    }
    
	/**
	 * Object to handle changes to a description field.
	 */
	private final TextFieldEditor.OnEditListener mEditListener = new TextFieldEditor.OnEditListener(){
		@Override
		public void onSaved(TextFieldEditor dialog, String newText) {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorSave(mDialogId, TextFieldEditorFragment.this, newText);
		}
		@Override
		public void onCancel(TextFieldEditor dialog) {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorCancel(mDialogId, TextFieldEditorFragment.this);
		}
	};
}
