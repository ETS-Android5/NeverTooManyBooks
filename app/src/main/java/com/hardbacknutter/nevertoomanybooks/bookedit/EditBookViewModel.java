/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.AutoCompleteTextField;
import com.hardbacknutter.nevertoomanybooks.fields.BitmaskChipGroupField;
import com.hardbacknutter.nevertoomanybooks.fields.CompoundButtonField;
import com.hardbacknutter.nevertoomanybooks.fields.DecimalEditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.EditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.EntityListDropDownMenuField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.RatingBarEditField;
import com.hardbacknutter.nevertoomanybooks.fields.StringArrayDropDownMenuField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LongNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class EditBookViewModel
        extends ViewModel {

    private static final String COULD_NOT_UPDATE = "Could not update";
    private static final String ORIGINAL = "original=";
    private static final String MODIFIED = "modified=";
    /** Log tag. */
    private static final String TAG = "EditBookViewModel";
    /** the list with all fields. */
    private final List<Field<?, ? extends View>> fields = new ArrayList<>();

    /** The key is the fragment tag. */
    private final Collection<FragmentId> fragmentsWithUnfinishedEdits =
            EnumSet.noneOf(FragmentId.class);

    private final MutableLiveData<List<Author>> authorList = new MutableLiveData<>();
    private final MutableLiveData<List<Series>> seriesList = new MutableLiveData<>();
    private final MutableLiveData<List<Publisher>> publisherList = new MutableLiveData<>();
    private final List<MenuHandler> menuHandlers = new ArrayList<>();
    private final Collection<FieldGroup> fieldGroups = EnumSet.noneOf(FieldGroup.class);

    /**
     * The Book we're editing (creating/updating).
     */
    private Book book;
    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    @Nullable
    private List<String> genres;
    /** Field drop down list. */
    @Nullable
    private List<String> locations;
    /** Field drop down list. */
    @Nullable
    private List<String> formats;
    /** Field drop down list. */
    @Nullable
    private List<String> colors;
    /** Field drop down list. */
    @Nullable
    private List<String> languagesCodes;
    /** Field drop down list. */
    @Nullable
    private List<String> pricePaidCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> listPriceCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> mAuthorNamesFormatted;
    /** Field drop down list. */
    @Nullable
    private List<String> authorFamilyNames;
    /** Field drop down list. */
    @Nullable
    private List<String> authorGivenNames;
    /** Field drop down list. */
    @Nullable
    private List<String> publisherNames;
    /** Field drop down list. */
    @Nullable
    private List<String> seriesTitles;

    /** The currently displayed tab. */
    private int currentTab;

    /** These FieldFormatters can be shared between multiple fields. */
    private FieldFormatter<String> dateFormatter;
    private FieldFormatter<String> languageFormatter;
    private ListFormatter<Entity> normalDetailListFormatter;
    private DoubleNumberFormatter doubleNumberFormatter;

    private boolean changed;

    private String errStrNonBlankRequired;
    private String errStrReadStartAfterEnd;

    int getCurrentTab() {
        return currentTab;
    }

    void setCurrentTab(final int currentTab) {
        this.currentTab = currentTab;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @Nullable final Bundle args) {

        if (book == null) {
            errStrNonBlankRequired = context.getString(R.string.vldt_non_blank_required);
            errStrReadStartAfterEnd = context.getString(R.string.vldt_read_start_after_end);

            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            dateFormatter = new DateFieldFormatter(locale);
            languageFormatter = new LanguageFormatter(locale);
            normalDetailListFormatter = new ListFormatter<>(Details.Normal, null);
            doubleNumberFormatter = new DoubleNumberFormatter();

            if (args != null) {
                // 1. Do we have a bundle? e.g. after an internet search
                final Bundle bookData = args.getBundle(Book.BKEY_DATA_BUNDLE);
                if (bookData != null) {
                    book = Book.from(bookData);
                } else {
                    // 2. Do we have an id?, e.g. user clicked on a book in a list.
                    final long bookId = args.getLong(DBKey.FK_BOOK, 0);
                    if (bookId > 0) {
                        book = Book.from(bookId);
                    } else {
                        book = new Book();
                    }
                    // has unchanged data, hence 'WriteAble'
                    book.setStage(EntityStage.Stage.WriteAble);
                }
            } else {
                // 3. No args, we want an empty new book (e.g. user wants to add one manually).
                book = new Book();
                // has no data, hence 'WriteAble'
                book.setStage(EntityStage.Stage.WriteAble);
            }

            book.addValidators();
            book.ensureBookshelf(context);
            book.ensureLanguage(context);
        }
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        if (menuHandlers.isEmpty()) {
            menuHandlers.add(new ViewBookOnWebsiteHandler());
            menuHandlers.add(new AmazonHandler());
        }
        return menuHandlers;
    }

    @NonNull
    List<Field<?, ? extends View>> getFields(@NonNull final FragmentId fragmentId) {
        return fields.stream()
                     .filter(field -> field.getFragmentId() == fragmentId)
                     .collect(Collectors.toList());
    }

    /**
     * Find the Field (across all fragments) associated with the passed ID.
     * If two fragments contain the same field (id) (which really should never happen... flw),
     * the first one found is returned.
     *
     * @param id Field/View ID
     *
     * @return Optional with the Field
     */
    @NonNull
    Optional<Field<?, ? extends View>> getField(@IdRes final int id) {
        return fields.stream()
                     .filter(field -> field.getFieldViewId() == id)
                     .findFirst();
    }

    /**
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field id was not found
     */
    @NonNull
    <T, V extends View> Field<T, V> requireField(@IdRes final int id) {
        //noinspection unchecked
        return (Field<T, V>) fields
                .stream()
                .filter(field -> field.getFieldViewId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + id));
    }

    /**
     * Save the values of the specified field group to the given Book.
     *
     * @param book to put the field values in
     */
    void saveFields(@NonNull final FragmentId fragmentId,
                    @NonNull final Book book) {
        getFields(fragmentId).stream()
                             .filter(Field::isAutoPopulated)
                             .forEach(field -> field.putValue(book));
    }

    /**
     * Get the list of fragments (their tags) which have unfinished edits.
     *
     * @return list
     */
    @NonNull
    Collection<FragmentId> getUnfinishedEdits() {
        return fragmentsWithUnfinishedEdits;
    }

    @NonNull
    Book getBook() {
        return book;
    }

    /**
     * Insert/update the book into the database, store cover files, and prepare activity results.
     *
     * @param context Current context
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    void saveBook(@NonNull final Context context)
            throws StorageException, DaoWriteException {

        if (book.isNew()) {
            ServiceLocator.getInstance().getBookDao().insert(context, book, 0);
        } else {
            ServiceLocator.getInstance().getBookDao().update(context, book, 0);
        }
        changed = true;
        book.setStage(EntityStage.Stage.Clean);
    }

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     * Delete an individual {@link TocEntry}.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    boolean deleteTocEntry(@NonNull final Context context,
                           @NonNull final TocEntry tocEntry) {
        return ServiceLocator.getInstance().getTocEntryDao().delete(context, tocEntry);
    }

    /**
     * Get the primary book Author.
     *
     * @param context Current context
     *
     * @return primary book author (or 'unknown' if none)
     */
    @NonNull
    Author getPrimaryAuthor(@NonNull final Context context) {
        return Objects.requireNonNullElseGet(book.getPrimaryAuthor(),
                                             () -> Author.createUnknownAuthor(context));
    }

    /**
     * Check if the book already exists in the database.
     *
     * @return {@code true} if it does
     */
    boolean bookExists() {
        if (book.isNew()) {
            final String isbnStr = book.getString(DBKey.BOOK_ISBN);
            if (!isbnStr.isEmpty()) {
                return ServiceLocator.getInstance().getBookDao().bookExistsByIsbn(isbnStr);
            }
        }

        return false;
    }

    /**
     * Add any fields the book does not have yet (does not overwrite existing ones).
     *
     * @param args to check
     */
    void addFieldsFromBundle(@Nullable final Bundle args) {
        if (args != null) {
            final Bundle bookData = args.getBundle(Book.BKEY_DATA_BUNDLE);
            if (bookData != null) {
                bookData.keySet()
                        .stream()
                        .filter(key -> !book.contains(key))
                        .forEach(key -> book.put(key, bookData.get(key)));
            }
        }
    }

    @NonNull
    List<Bookshelf> getAllBookshelves() {
        // not cached.
        // This allows the user to edit the global list of shelves while editing a book.
        return ServiceLocator.getInstance().getBookshelfDao().getAll();
    }

    /**
     * Load an {@link Author} names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorNames() {
        if (mAuthorNamesFormatted == null) {
            mAuthorNamesFormatted = ServiceLocator.getInstance().getAuthorDao()
                                                  .getNames(DBKey.KEY_AUTHOR_FORMATTED);
        }
        return mAuthorNamesFormatted;
    }

    /**
     * Load an {@link Author} Family names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorFamilyNames() {
        if (authorFamilyNames == null) {
            authorFamilyNames = ServiceLocator.getInstance().getAuthorDao()
                                              .getNames(DBKey.AUTHOR_FAMILY_NAME);
        }
        return authorFamilyNames;
    }

    /**
     * Load an {@link Author} Given names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorGivenNames() {
        if (authorGivenNames == null) {
            authorGivenNames = ServiceLocator.getInstance().getAuthorDao()
                                             .getNames(DBKey.AUTHOR_GIVEN_NAMES);
        }
        return authorGivenNames;
    }

    /**
     * Load a {@link Publisher} names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllPublisherNames() {
        if (publisherNames == null) {
            publisherNames = ServiceLocator.getInstance().getPublisherDao().getNames();
        }
        return publisherNames;
    }

    /**
     * Load a {@link Series} titles list.
     *
     * @return list of titles
     */
    @NonNull
    List<String> getAllSeriesTitles() {
        if (seriesTitles == null) {
            seriesTitles = ServiceLocator.getInstance().getSeriesDao().getNames();
        }
        return seriesTitles;
    }

    /**
     * Add or remove the given fragment tag from the list of unfinished edits.
     *
     * @param fragmentId         of fragment
     * @param hasUnfinishedEdits flag
     */
    void setUnfinishedEdits(@NonNull final FragmentId fragmentId,
                            final boolean hasUnfinishedEdits) {
        if (hasUnfinishedEdits) {
            // Flag up this fragment as having unfinished edits.
            fragmentsWithUnfinishedEdits.add(fragmentId);
        } else {
            fragmentsWithUnfinishedEdits.remove(fragmentId);
        }
    }

    /**
     * Load a language list.
     * <p>
     * Returns a unique list of all languages in the database.
     * The list is ordered by {@link DBKey#DATE_LAST_UPDATED__UTC}.
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    private List<String> getAllLanguagesCodes() {
        if (languagesCodes == null) {
            languagesCodes = ServiceLocator.getInstance().getLanguageDao().getList();
        }
        return languagesCodes;
    }

    /**
     * Load a format list.
     *
     * @return List of formats
     */
    @NonNull
    private List<String> getAllFormats() {
        if (formats == null) {
            formats = ServiceLocator.getInstance().getFormatDao().getList();
        }
        return formats;
    }

    /**
     * Load a color list.
     *
     * @return List of colors
     */
    @NonNull
    private List<String> getAllColors() {
        if (colors == null) {
            colors = ServiceLocator.getInstance().getColorDao().getList();
        }
        return colors;
    }

    /**
     * Load a genre list.
     *
     * @return List of genres
     */
    @NonNull
    private List<String> getAllGenres() {
        if (genres == null) {
            genres = ServiceLocator.getInstance().getGenreDao().getList();
        }
        return genres;
    }

    /**
     * Load a location list.
     *
     * @return List of locations
     */
    @NonNull
    private List<String> getAllLocations() {
        if (locations == null) {
            locations = ServiceLocator.getInstance().getLocationDao().getList();
        }
        return locations;
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getAllListPriceCurrencyCodes() {
        if (listPriceCurrencies == null) {
            listPriceCurrencies = ServiceLocator
                    .getInstance().getBookDao().getCurrencyCodes(DBKey.PRICE_LISTED_CURRENCY);
        }
        return listPriceCurrencies;
    }

    /**
     * Check if the passed Author is only used by this book.
     *
     * @param context Current context
     * @param author  to check
     *
     * @return {@code true} if the Author is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Author author) {
        final Locale bookLocale = book.getLocale(context);

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        final long nrOfReferences = authorDao.countBooks(context, author, bookLocale)
                                    + authorDao.countTocEntries(context, author, bookLocale);
        return nrOfReferences <= (book.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Series is only used by this book.
     *
     * @param context Current context
     * @param series  to check
     *
     * @return {@code true} if the Series is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Series series) {
        final Locale bookLocale = book.getLocale(context);
        final long nrOfReferences = ServiceLocator.getInstance().getSeriesDao()
                                                  .countBooks(context, series, bookLocale);
        return nrOfReferences <= (book.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Publisher is only used by this book.
     *
     * @param context   Current context
     * @param publisher to check
     *
     * @return {@code true} if the Publisher is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Publisher publisher) {
        final Locale bookLocale = book.getLocale(context);
        final long nrOfReferences = ServiceLocator.getInstance().getPublisherDao()
                                                  .countBooks(context, publisher, bookLocale);
        return nrOfReferences <= (book.isNew() ? 0 : 1);
    }

    @NonNull
    LiveData<List<Author>> onAuthorList() {
        return authorList;
    }

    @NonNull
    LiveData<List<Series>> onSeriesList() {
        return seriesList;
    }

    @NonNull
    LiveData<List<Publisher>> onPublisherList() {
        return publisherList;
    }

    void updateAuthors(@NonNull final List<Author> list) {
        book.setAuthors(list);
        authorList.setValue(list);
    }

    void updateSeries(@NonNull final List<Series> list) {
        book.setSeries(list);
        seriesList.setValue(list);
    }

    void updatePublishers(@NonNull final List<Publisher> list) {
        book.setPublishers(list);
        publisherList.setValue(list);
    }

    boolean changeForThisBook(@NonNull final Context context,
                              @NonNull final Author original,
                              @NonNull final Author modified) {

        if (ServiceLocator.getInstance().getAuthorDao().insert(context, modified) > 0) {
            final List<Author> list = book.getAuthors();
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            book.setAuthors(list);
            book.pruneAuthors(context, true);
            return true;
        }
        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    boolean changeForAllBooks(@NonNull final Context context,
                              @NonNull final Author original,
                              @NonNull final Author modified) {
        // copy all new data
        original.copyFrom(modified, true);

        if (ServiceLocator.getInstance().getAuthorDao().update(context, original)) {
            book.pruneAuthors(context, true);
            book.refreshAuthorList(context);
            return true;
        }

        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    boolean changeForThisBook(@NonNull final Context context,
                              @NonNull final Series original,
                              @NonNull final Series modified) {
        if (ServiceLocator.getInstance().getSeriesDao()
                          .insert(context, modified, book.getLocale(context)) > 0) {
            final List<Series> list = book.getSeries();
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            book.setSeries(list);
            book.pruneSeries(context, true);
            return true;
        }

        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    boolean changeForAllBooks(@NonNull final Context context,
                              @NonNull final Series original,
                              @NonNull final Series modified) {
        // copy all new data
        original.copyFrom(modified, true);

        if (ServiceLocator.getInstance().getSeriesDao()
                          .update(context, original, book.getLocale(context))) {
            book.pruneSeries(context, true);
            book.refreshSeriesList(context);
            return true;
        }
        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    boolean changeForThisBook(@NonNull final Context context,
                              @NonNull final Publisher original,
                              @NonNull final Publisher modified) {

        if (ServiceLocator.getInstance().getPublisherDao()
                          .insert(context, modified, book.getLocale(context)) > 0) {
            final List<Publisher> list = book.getPublishers();
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            book.setPublishers(list);
            book.prunePublishers(context, true);
            return true;
        }
        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    boolean changeForAllBooks(@NonNull final Context context,
                              @NonNull final Publisher original,
                              @NonNull final Publisher modified) {
        // copy all new data
        original.copyFrom(modified);

        if (ServiceLocator.getInstance().getPublisherDao()
                          .update(context, original, book.getLocale(context))) {
            book.prunePublishers(context, true);
            book.refreshPublishersList(context);
            return true;
        }
        Logger.error(TAG, new Throwable(), COULD_NOT_UPDATE, ORIGINAL + original,
                     MODIFIED + modified);
        return false;
    }

    void fixId(@NonNull final Context context,
               @NonNull final Author author) {
        ServiceLocator.getInstance().getAuthorDao()
                      .fixId(context, author, true, book.getLocale(context));
    }

    void fixId(@NonNull final Context context,
               @NonNull final Series series) {
        ServiceLocator.getInstance().getSeriesDao()
                      .fixId(context, series, true, book.getLocale(context));
    }

    void fixId(@NonNull final Context context,
               @NonNull final Publisher publisher) {
        ServiceLocator.getInstance().getPublisherDao()
                      .fixId(context, publisher, true, book.getLocale(context));
    }

    void fixId(@NonNull final Context context,
               @NonNull final TocEntry tocEntry) {
        ServiceLocator.getInstance().getTocEntryDao()
                      .fixId(context, tocEntry, true, book.getLocale(context));
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getAllPricePaidCurrencyCodes() {
        if (pricePaidCurrencies == null) {
            pricePaidCurrencies = ServiceLocator
                    .getInstance().getBookDao().getCurrencyCodes(DBKey.PRICE_PAID_CURRENCY);
        }
        return pricePaidCurrencies;
    }

    /**
     * Init all Fields, and add them to the fields collection.
     * <p>
     * Note that Field views are <strong>NOT AVAILABLE</strong> at this time.
     * The context must NOT be stored.
     * <p>
     * Called from {@link EditBookBaseFragment#onViewCreated}.
     * The fields will be populated in {@link EditBookBaseFragment#onPopulateViews}
     *
     * @param context    Current context
     * @param fragmentId the hosting fragment for this set of fields
     * @param fieldGroup to create the fields for
     */
    void initFields(@NonNull final Context context,
                    @NonNull final FragmentId fragmentId,
                    @NonNull final FieldGroup fieldGroup) {

        // init once only for each group
        if (fieldGroups.contains(fieldGroup)) {
            return;
        }
        fieldGroups.add(fieldGroup);

        switch (fieldGroup) {
            case Main:
                initFieldsMain(fragmentId);
                break;
            case Publication:
                initFieldsPublication(fragmentId);
                break;
            case Notes:
                initFieldsNotes(context, fragmentId);
                break;
            case Toc:
                initFieldsToc(context, fragmentId);
                break;
            case ExternalId:
                initFieldsExternalId(fragmentId);
                break;
        }
    }

    private void initFieldsMain(@NonNull final FragmentId fragmentId) {

        fields.add(new TextViewField<>(fragmentId, R.id.author, Book.BKEY_AUTHOR_LIST,
                                       DBKey.FK_AUTHOR,
                                       normalDetailListFormatter)
                           .setTextInputLayoutId(R.id.lbl_author)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        fields.add(new TextViewField<>(fragmentId, R.id.series_title, Book.BKEY_SERIES_LIST,
                                       DBKey.FK_SERIES,
                                       normalDetailListFormatter)
                           .setTextInputLayoutId(R.id.lbl_series));

        fields.add(new EditTextField<>(fragmentId, R.id.title, DBKey.TITLE)
                           .setTextInputLayoutId(R.id.lbl_title)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        fields.add(new EditTextField<>(fragmentId, R.id.description, DBKey.DESCRIPTION)
                           .setTextInputLayoutId(R.id.lbl_description)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        fields.add(new EditTextField<>(fragmentId, R.id.isbn, DBKey.BOOK_ISBN)
                           .setTextInputLayoutId(R.id.lbl_isbn));
        // don't do this for now. There is a scan icon as end-icon.
        //                  .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)

        fields.add(new AutoCompleteTextField(fragmentId, R.id.language, DBKey.LANGUAGE,
                                             languageFormatter, true,
                                             this::getAllLanguagesCodes)
                           .setTextInputLayoutId(R.id.lbl_language)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        fields.add(new AutoCompleteTextField(fragmentId, R.id.genre, DBKey.GENRE,
                                             this::getAllGenres)
                           .setTextInputLayoutId(R.id.lbl_genre));

        // Personal fields

        fields.add(new TextViewField<>(fragmentId, R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                       DBKey.FK_BOOKSHELF,
                                       normalDetailListFormatter)
                           .setTextInputLayoutId(R.id.lbl_bookshelves)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));
    }

    private void initFieldsPublication(@NonNull final FragmentId fragmentId) {

        fields.add(new AutoCompleteTextField(fragmentId, R.id.format, DBKey.FORMAT,
                                             this::getAllFormats)
                           .setTextInputLayoutId(R.id.lbl_format));

        fields.add(new AutoCompleteTextField(fragmentId, R.id.color, DBKey.COLOR,
                                             this::getAllColors)
                           .setTextInputLayoutId(R.id.lbl_color));

        fields.add(new TextViewField<>(fragmentId, R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                       DBKey.FK_PUBLISHER,
                                       normalDetailListFormatter)
                           .setTextInputLayoutId(R.id.lbl_publisher));

        fields.add(new TextViewField<>(fragmentId, R.id.first_publication,
                                       DBKey.FIRST_PUBLICATION__DATE,
                                       dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_first_publication)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new TextViewField<>(fragmentId, R.id.date_published,
                                       DBKey.BOOK_PUBLICATION__DATE,
                                       dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_date_published)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.pages, DBKey.PAGE_COUNT)
                           .setTextInputLayoutId(R.id.lbl_pages)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));


        // MUST be defined before the currency field is defined.
        fields.add(new DecimalEditTextField(fragmentId, R.id.price_listed, DBKey.PRICE_LISTED,
                                            doubleNumberFormatter)
                           .setTextInputLayoutId(R.id.lbl_price_listed)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new AutoCompleteTextField(fragmentId, R.id.price_listed_currency,
                                             DBKey.PRICE_LISTED_CURRENCY,
                                             this::getAllListPriceCurrencyCodes)
                           .setTextInputLayoutId(R.id.lbl_price_listed_currency)
                           .addRelatedViews(R.id.lbl_price_listed, R.id.price_listed_currency));

        fields.add(new EditTextField<>(fragmentId, R.id.print_run, DBKey.PRINT_RUN)
                           .setTextInputLayoutId(R.id.lbl_print_run)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new BitmaskChipGroupField(fragmentId, R.id.edition, DBKey.EDITION__BITMASK,
                                             Book.Edition::getAll)
                           .addRelatedViews(R.id.lbl_edition));
    }

    private void initFieldsNotes(@NonNull final Context context,
                                 @NonNull final FragmentId fragmentId) {
        fields.add(new CompoundButtonField(fragmentId, R.id.cbx_read, DBKey.READ__BOOL));

        fields.add(new CompoundButtonField(fragmentId, R.id.cbx_signed, DBKey.SIGNED__BOOL));

        fields.add(new RatingBarEditField(fragmentId, R.id.rating, DBKey.RATING));

        fields.add(new EditTextField<>(fragmentId, R.id.notes, DBKey.PERSONAL_NOTES)
                           .setTextInputLayoutId(R.id.lbl_notes)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        // MUST be defined before the currency.
        fields.add(new DecimalEditTextField(fragmentId, R.id.price_paid, DBKey.PRICE_PAID,
                                            doubleNumberFormatter)
                           .setTextInputLayoutId(R.id.lbl_price_paid)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new AutoCompleteTextField(fragmentId, R.id.price_paid_currency,
                                             DBKey.PRICE_PAID_CURRENCY,
                                             this::getAllPricePaidCurrencyCodes)
                           .setTextInputLayoutId(R.id.lbl_price_paid_currency)
                           .addRelatedViews(R.id.lbl_price_paid, R.id.price_paid_currency));

        fields.add(new StringArrayDropDownMenuField(fragmentId, R.id.condition,
                                                    DBKey.BOOK_CONDITION,
                                                    context, R.array.conditions_book)
                           .setTextInputLayoutId(R.id.lbl_condition));

        fields.add(new StringArrayDropDownMenuField(fragmentId, R.id.condition_cover,
                                                    DBKey.BOOK_CONDITION_COVER,
                                                    context, R.array.conditions_dust_cover)
                           .setTextInputLayoutId(R.id.lbl_condition_cover));

        fields.add(new AutoCompleteTextField(fragmentId, R.id.location, DBKey.LOCATION,
                                             this::getAllLocations)
                           .setTextInputLayoutId(R.id.lbl_location));

        fields.add(new TextViewField<>(fragmentId, R.id.date_acquired, DBKey.DATE_ACQUIRED,
                                       dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_date_acquired)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new TextViewField<>(fragmentId, R.id.read_start, DBKey.READ_START__DATE,
                                       dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_read_start)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(this::validateReadStartAndEndFields));

        fields.add(new TextViewField<>(fragmentId, R.id.read_end, DBKey.READ_END__DATE,
                                       dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_read_end)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(this::validateReadStartAndEndFields));
    }

    private void validateReadStartAndEndFields(@NonNull final Field<String, TextView> field) {

        // we ignore the passed field, so we can use this validator for both fields.
        final Field<String, TextView> startField = requireField(R.id.read_start);
        final Field<String, TextView> endField = requireField(R.id.read_end);

        final String start = startField.getValue();
        if (start == null || start.isEmpty()) {
            startField.setError(null);
            endField.setError(null);
            return;
        }

        final String end = endField.getValue();
        if (end == null || end.isEmpty()) {
            startField.setError(null);
            endField.setError(null);
            return;
        }

        if (start.compareToIgnoreCase(end) > 0) {
            endField.setError(errStrReadStartAfterEnd);

        } else {
            startField.setError(null);
            endField.setError(null);
        }
    }

    private void initFieldsToc(@NonNull final Context context,
                               @NonNull final FragmentId fragmentId) {
        fields.add(new EntityListDropDownMenuField<>(fragmentId, R.id.book_type,
                                                     DBKey.TOC_TYPE__BITMASK,
                                                     context,
                                                     Book.ContentType.getAll())
                           .setTextInputLayoutId(R.id.lbl_book_type));
    }

    private void initFieldsExternalId(@NonNull final FragmentId fragmentId) {

        // These FieldFormatters can be shared between multiple fields.
        final FieldFormatter<Number> longNumberFormatter = new LongNumberFormatter();

        fields.add(new EditTextField<>(fragmentId, R.id.site_goodreads, DBKey.SID_GOODREADS_BOOK,
                                       longNumberFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_site_goodreads)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.site_isfdb, DBKey.SID_ISFDB,
                                       longNumberFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_site_isfdb)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.site_library_thing,
                                       DBKey.SID_LIBRARY_THING,
                                       longNumberFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_site_library_thing)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.site_open_library, DBKey.SID_OPEN_LIBRARY)
                           .setTextInputLayoutId(R.id.lbl_site_open_library)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.site_strip_info_be, DBKey.SID_STRIP_INFO,
                                       longNumberFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_site_strip_info_be)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        fields.add(new EditTextField<>(fragmentId, R.id.site_last_dodo_nl, DBKey.SID_LAST_DODO_NL,
                                       longNumberFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_site_last_dodo_nl)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));
    }

    /**
     * Check if the given fragment handles (displays) the given field.
     */
    public boolean handlesField(@NonNull final FragmentId fragmentId,
                                final int fieldId) {
        return fields.stream()
                     // This will return a single field (or none)
                     .filter(field -> field.getFieldViewId() == fieldId)
                     // lets see if its owned by the given fragment
                     .anyMatch(field -> field.getFragmentId() == fragmentId);
    }
}
