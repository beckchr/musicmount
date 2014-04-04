/*
 * Copyright 2013-2014 Odysseus Software GmbH
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.musicmount.builder.model.Titled;

public class CollectionSection<T extends Titled> implements Comparable<CollectionSection<T>> {
	private static final Character NON_LETTER_SECTION_KEY = '#';
	private static final Character UNTITLED_SECTION_KEY = '?';

	/**
	 * Partition items into index sections (capital letters plus '#').
	 * @param <T> titled type
	 * @param items items to be indexed
	 * @param comparator used to compare items
	 * @return sections ('A', ..., 'Z', '#', '?')
	 */
	public static <T extends Titled> Iterable<CollectionSection<T>> createIndex(Iterable<? extends T> items, TitledComparator<T> comparator) {
		Map<Character, CollectionSection<T>> sectionMap = new HashMap<Character, CollectionSection<T>>();
		for (T item : items) {
			Character sectionKey = null;
			if (item.getTitle() != null && item.getTitle().trim().length() > 0) {
				char first = Normalizer.normalize(comparator.sortTitle(item), Normalizer.Form.NFD).charAt(0);
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

		List<CollectionSection<T>> sections = new ArrayList<CollectionSection<T>>();
		for (Map.Entry<Character, CollectionSection<T>> entry : sectionMap.entrySet()) {
			Collections.sort(entry.getValue().getItems(), comparator);
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
		return sections;
	}
	
	private final String title;
	private final ArrayList<T> items = new ArrayList<T>();
	
	public CollectionSection(String title) {
		this.title = title;
	}
	
	public String getTitle() {
		return title;
	}
	
	public ArrayList<T> getItems() {
		return items;
	}
	
	public int compareTo(CollectionSection<T> o) {
		if (title == o.title) {
			return 0;
		}
		if (title == null) {
			return -1;
		}
		if (o.title == null) {
			return +1;
		}
		return title.compareTo(o.title);
	}
}