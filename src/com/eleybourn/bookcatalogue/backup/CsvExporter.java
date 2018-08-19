/*
* @copyright 2013 Evan Leybourn
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
package com.eleybourn.bookcatalogue.backup;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Date;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookEditFields;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implementation of Exporter that creates a CSV file.
 * 
 * @author pjw
 */
public class CsvExporter implements Exporter {
	private String mLastError;

	private static final String UTF8 = "utf8";
	private static final int BUFFER_SIZE = 32768;

	public String getLastError() {
		return mLastError;
	}

	public boolean export(OutputStream outputStream, Exporter.ExportListener listener, final int backupFlags, Date since) throws IOException {
		final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);
		final String AUTHOR = BookCatalogueApp.getResourceString(R.string.author);

		/* RELEASE: Handle flags! */
		int num = 0;
		if (!StorageUtils.sdCardWritable()) {
			mLastError = "Export Failed - Could not write to SDCard";
			return false;			
		}

		// Fix the 'since' date, if required
		if ( (backupFlags & Exporter.EXPORT_SINCE) != 0) {
			if (since == null) {
				mLastError = "Export Failed - 'since' is null";
				return false;			
			}
		} else {
			since = null;
		}

		// Display startup message
		listener.onProgress(BookCatalogueApp.getResourceString(R.string.export_starting_ellipsis), 0);
		boolean displayingStartupMessage = true;

		StringBuilder export = new StringBuilder(
			'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
			'"' + CatalogueDBAdapter.KEY_AUTHOR_DETAILS + "\"," + 	//2
			'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
			'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
			'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
			'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
			'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
			'"' + "bookshelf_id\"," + 								//9
			'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
			'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
			'"' + CatalogueDBAdapter.KEY_SERIES_DETAILS + "\"," +	//12
			'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
			'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
			'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
			'"' + CatalogueDBAdapter.KEY_ANTHOLOGY_MASK+ "\"," + 		//17
			'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
			'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
			'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
			'"' + CatalogueDBAdapter.KEY_FORMAT+ "\"," + 			//21
			'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
			'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
			'"' + "anthology_titles" + "\"," +						//24 
			'"' + CatalogueDBAdapter.KEY_DESCRIPTION+ "\"," + 		//25
			'"' + CatalogueDBAdapter.KEY_GENRE+ "\"," + 			//26
			'"' + DatabaseDefinitions.DOM_LANGUAGE+ "\"," + 			//+1
			'"' + CatalogueDBAdapter.KEY_DATE_ADDED+ "\"," + 		//27
			'"' + DatabaseDefinitions.DOM_GOODREADS_BOOK_ID + "\"," + 		//28
			'"' + DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE + "\"," + 		//29
			'"' + DatabaseDefinitions.DOM_LAST_UPDATE_DATE + "\"," + 		//30
			'"' + DatabaseDefinitions.DOM_BOOK_UUID + "\"," + 		//31
			"\n");
		
		long lastUpdate = 0;
		
		StringBuilder row = new StringBuilder();

		CatalogueDBAdapter db;
		db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
		db.open();		

		BooksCursor books = db.exportBooks(since);
		BooksRowView rv = books.getRowView();

		try {
			final int totalBooks = books.getCount();

			if (!listener.isCancelled()) {
	
				listener.setMax(totalBooks);

				/* write to the SDCard */
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE);
				out.write(export.toString());
				if (books.moveToFirst()) {
					do { 
						num++;
						long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateString = "";
						try {
							dateString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
						} catch (Exception e) {
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateReadStartString = "";
						try {
							dateReadStartString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
						} catch (Exception e) {
							Logger.logError(e);
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateReadEndString = "";
						try {
							dateReadEndString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END));
						} catch (Exception e) {
							Logger.logError(e);
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateAddedString = "";
						try {
							dateAddedString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_ADDED));
						} catch (Exception e) {
							//do nothing
						}

						int anthology = books.getInt(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
						StringBuilder anthology_titles = new StringBuilder();
						if (anthology != 0) {
							Cursor titles = db.fetchAnthologyTitlesByBook(id);
							try {
								if (titles.moveToFirst()) {
									do { 
										String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
										String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
										anthology_titles.append(anth_title).append(" * ").append(anth_author).append("|");
									} while (titles.moveToNext()); 
								}
							} finally {
								if (titles != null)
									titles.close();
							}
						}
						String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
						// Sanity check: ensure title is non-blank. This has not happened yet, but we 
						// know if does for author, so completeness suggests making sure all 'required'
						// fields are non-blank.
						if (title == null || title.trim().isEmpty())
							title = UNKNOWN;

						//Display the selected bookshelves
						Cursor bookshelves = db.fetchAllBookshelvesByBook(id);
						StringBuilder bookshelves_id_text = new StringBuilder();
						StringBuilder bookshelves_name_text = new StringBuilder();
						while (bookshelves.moveToNext()) {
							bookshelves_id_text.append(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID))).append(BookEditFields.BOOKSHELF_SEPERATOR);
							bookshelves_name_text.append(Utils.encodeListItem(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)), BookEditFields.BOOKSHELF_SEPERATOR)).append(BookEditFields.BOOKSHELF_SEPERATOR);
						}
						bookshelves.close();

						String authorDetails = Utils.getAuthorUtils().encodeList( db.getBookAuthorList(id), '|' );
						// Sanity check: ensure author is non-blank. This HAPPENS. Probably due to constraint failures.
						if (authorDetails == null || authorDetails.trim().isEmpty())
							authorDetails = AUTHOR + ", " + UNKNOWN;

						String seriesDetails = Utils.getSeriesUtils().encodeList( db.getBookSeriesList(id), '|' );

						row.setLength(0);
						row.append("\"").append(formatCell(id)).append("\",");
						row.append("\"").append(formatCell(authorDetails)).append("\",");
						row.append("\"").append(formatCell(title)).append("\",");
						row.append("\"").append(formatCell(rv.getIsbn())).append("\",");
						row.append("\"").append(formatCell(rv.getPublisher())).append("\",");
						row.append("\"").append(formatCell(dateString)).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)))).append("\",");
						row.append("\"").append(formatCell(bookshelves_id_text)).append("\",");
						row.append("\"").append(formatCell(bookshelves_name_text)).append("\",");
						row.append("\"").append(formatCell(rv.getRead())).append("\",");
						row.append("\"").append(formatCell(seriesDetails)).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)))).append("\",");
						row.append("\"").append(formatCell(rv.getNotes())).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)))).append("\",");
						row.append("\"").append(formatCell(anthology)).append("\",");
						row.append("\"").append(formatCell(rv.getLocation())).append("\",");
						row.append("\"").append(formatCell(dateReadStartString)).append("\",");
						row.append("\"").append(formatCell(dateReadEndString)).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT)))).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED)))).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO)) + "")).append("\",");
						row.append("\"").append(formatCell(anthology_titles.toString())).append("\",");
						row.append("\"").append(formatCell(rv.getDescription())).append("\",");
						row.append("\"").append(formatCell(rv.getGenre())).append("\",");
						row.append("\"").append(formatCell(rv.getLanguage())).append("\",");
						row.append("\"").append(formatCell(dateAddedString)).append("\",");
						row.append("\"").append(formatCell(rv.getGoodreadsBookId())).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE.name)))).append("\",");
						row.append("\"").append(formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name)))).append("\",");
						row.append("\"").append(formatCell(rv.getBookUuid())).append("\",");
						row.append("\n");
						out.write(row.toString());

						long now = System.currentTimeMillis();
						if ( (now - lastUpdate) > 200) {
							if (displayingStartupMessage) {
								listener.onProgress("",0);
								displayingStartupMessage = false;
							}
							listener.onProgress(title, num);
							lastUpdate = now;
						}
					}
					while (books.moveToNext() && !listener.isCancelled()); 
				} 
				
				out.close();
			}
	
		} finally {
			System.out.println("Books Exported: " + num);
			if (displayingStartupMessage) {
				try {
					listener.onProgress("", 0);
					displayingStartupMessage = false;
				} catch (Exception ignored) {
				}
			}
			try { books.close(); } catch (Exception ignored) {}
            db.close();
		}
		return true;
	}

	private String formatCell(StringBuilder cell) {
		return cell.toString();
	}

	private String formatCell(long cell) {
		String newcell = cell + "";
		return formatCell(newcell);
	}
    /**
     * Double quote all "'s and remove all newlines
     *
     * @param cell The cell the format
     * @return The formatted cell
     */
	private String formatCell(String cell) {
		try {
			if (cell.equals("null") || cell.trim().isEmpty()) {
				return "";
			}
			StringBuilder bld = new StringBuilder();
			int endPos = cell.length() - 1;
			int pos = 0;
			while (pos <= endPos) {
				char c = cell.charAt(pos);
				switch(c) {
				case '\r':
					bld.append("\\r");
					break;
				case '\n':
					bld.append("\\n");
					break;
				case '\t':
					bld.append("\\t");
					break;
				case '"':
					bld.append("\"\"");
					break;
				case '\\':
					bld.append("\\\\");
					break;
				default:
					bld.append(c);
				}
				pos++;

			}
			return bld.toString();
		} catch (NullPointerException e) {
			return "";
		}
	}
	
}
