/*
 * Copyright 2013 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.musicmount.builder.impl;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.musicmount.builder.model.Titled;

public class CollectionSectionIndex<T extends Titled> {
	private static final Character NON_LETTER_SECTION_KEY = '#';
	private static final Character UNTITLED_SECTION_KEY = '?';

	private final List<CollectionSection<T>> sections;
	
	/**
	 * Partition items into index sections (capital letters plus '#').
	 * @param items items to be indexed
	 * @param secondaryItemComparator used to compare items with equal titles (may be <code>null</code>)
	 */
	public CollectionSectionIndex(final LocalStrings localStrings, Iterable<? extends T> items, final String defaultTitle, final Comparator<T> secondaryItemComparator) {
		final String[] sortTitlePrefixes = localStrings.getSortTitlePrefixes();
		Map<Character, CollectionSection<T>> sectionMap = new HashMap<Character, CollectionSection<T>>();
		for (T item : items) {
			Character sectionKey = null;
			if (item.getTitle() != null && item.getTitle().trim().length() > 0) {
				char first = Normalizer.normalize(sortTitle(item.getTitle(), sortTitlePrefixes), Normalizer.Form.NFD).charAt(0);
				if (Character.isLetter(first) && first < 128) {
					sectionKey = Character.toUpperCase(first);
				} else {
					sectionKey = NON_LETTER_SECTION_KEY;
				}
			} else { // untitled
				sectionKey = UNTITLED_SECTION_KEY;
			}
			CollectionSection<T> section = sectionMap.get(sectionKey);
			if (section == null) {
				sectionMap.put(sectionKey, section = new CollectionSection<T>(sectionKey.toString()));
			}
			section.getItems().add(item);
		}

		sections = new ArrayList<CollectionSection<T>>();
		final Collator collator = Collator.getInstance(localStrings.getLocale());
		collator.setStrength(Collator.SECONDARY);
		collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
		for (Map.Entry<Character, CollectionSection<T>> entry : sectionMap.entrySet()) {
			Collections.sort(entry.getValue().getItems(), new Comparator<T>() {
				public int compare(T o1, T o2) {
					String title1 = o1.getTitle() == null ? defaultTitle : o1.getTitle();
					String title2 = o2.getTitle() == null ? defaultTitle : o2.getTitle();
					int result = collator.compare(sortTitle(title1, sortTitlePrefixes), sortTitle(title2, sortTitlePrefixes));
					if (result == 0 && secondaryItemComparator != null) {
						result = secondaryItemComparator.compare(o1, o2);
					}
					return result;
				}
			});
			if (!entry.getKey().equals(NON_LETTER_SECTION_KEY) && !entry.getKey().equals(UNTITLED_SECTION_KEY)) { // append those later
				sections.add(entry.getValue());
			}
		}
		Collections.sort(sections);
		if (sectionMap.containsKey(NON_LETTER_SECTION_KEY)) {
			sections.add(sectionMap.get(NON_LETTER_SECTION_KEY));
		}
		if (sectionMap.containsKey(UNTITLED_SECTION_KEY)) {
			sections.add(sectionMap.get(UNTITLED_SECTION_KEY));
		}
	}

	public Iterable<CollectionSection<T>> getSections() {
		return sections;
	}
	
	String sortTitle(String title, String[] prefixes) {
		for (String prefix : prefixes) {
			if (title.length() > prefix.length() && title.toUpperCase().startsWith(prefix.toUpperCase())) {
				String suffix = title.substring(prefix.length()).trim();
				if (suffix.length() > 0) {
					title = suffix + ", " + title.substring(0, prefix.length()).trim();
					break;
				}
			}
		}
		int letterOrDigit = 0;
		while (letterOrDigit < title.length()) {
			char c = title.charAt(letterOrDigit);
			if (Character.isLetter(c) || Character.isDigit(c)) {
				break;
			}
			letterOrDigit++;
		}
		return letterOrDigit < title.length() ? title.substring(letterOrDigit) : title;
	}
}
