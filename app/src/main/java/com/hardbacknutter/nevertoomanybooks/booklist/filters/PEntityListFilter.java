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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 *
 * <ul>
 * <li>The value is a {@code Set<Long>} with the key being the entity id.</li>
 * <li>The Set is never {@code null}.</li>
 * <li>An empty Set indicates an inactive filter.</li>
 * </ul>
 */
public class PEntityListFilter<T extends Entity>
        implements PFilter<Set<Long>> {

    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_entity_list;

    @StringRes
    private final int labelResId;
    @NonNull
    private final String dbKey;
    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Supplier<List<T>> listSupplier;

    private final Set<Long> value = new HashSet<>();
    @Nullable
    private Map<Long, Entity> entityMap;

    PEntityListFilter(@NonNull final String dbKey,
                      @StringRes final int labelResId,
                      @NonNull final TableDefinition table,
                      @NonNull final Domain domain,
                      @NonNull final Supplier<List<T>> listSupplier) {
        this.dbKey = dbKey;
        this.labelResId = labelResId;
        this.table = table;
        this.domain = domain;
        this.listSupplier = listSupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!GlobalFieldVisibility.isUsed(domain.getName())) {
            return false;
        }
        return !value.isEmpty();
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        if (value.size() == 1) {
            return '(' + table.dot(domain) + '=' + value.toArray()[0] + ')';
        } else {
            return value.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(
                                ",",
                                '(' + table.dot(domain) + " IN ("
                                , "))"));
        }
    }

    @Override
    @NonNull
    public String getDBKey() {
        return dbKey;
    }

    @Nullable
    @Override
    public String getPersistedValue() {
        return value.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
    }

    @Override
    public void setPersistedValue(@Nullable final String csvString) {
        value.clear();
        if (csvString != null && !csvString.isEmpty()) {
            value.addAll(Arrays.stream(csvString.split(","))
                               .map(Long::parseLong)
                               .collect(Collectors.toList()));
        }
    }

    @NonNull
    public List<T> getEntities() {
        return listSupplier.get();
    }

    @NonNull
    @Override
    public Set<Long> getValue() {
        return new HashSet<>(value);
    }

    @Override
    public void setValue(@Nullable final Set<Long> value) {
        this.value.clear();
        if (value != null && !value.isEmpty()) {
            this.value.addAll(value);
        }
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context,
                               @Nullable final Set<Long> value) {
        if (value == null || value.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            if (entityMap == null) {
                entityMap = listSupplier
                        .get()
                        .stream()
                        .collect(Collectors.toMap(Entity::getId, entity -> entity));
            }
            //noinspection ConstantConditions
            return value.stream()
                        .map(entityMap::get)
                        .filter(Objects::nonNull)
                        .map(entity -> entity.getLabel(context))
                        .collect(Collectors.joining("; "));
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return LAYOUT_ID;
    }
}
