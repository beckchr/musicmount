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

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;

import org.musicmount.builder.model.Titled;

/**
 *Comparator for <code>Titled</code> objects.
 * 
 * @param <T> titled type
 */
public class TitledComparator<T extends Titled> implements Comparator<T> {
	private final Collator collator;
	private final String[] sortTitlePrefixes;
	private final String defaultTitle;
	private final Comparator<? super T> secondaryItemComparator;
	private final HashMap<T, String> sortTitles = new HashMap<>();

	/**
	 * @param localStrings locale and sort title prefixes ('a', 'the', ...)
	 * @param defaultTitle used when <code>titled.getTitle() == null</code>
	 * @param secondaryItemComparator used to compare items with equal titles (may be <code>null</code>)
	 */
	public TitledComparator(LocalStrings localStrings, final String defaultTitle, final Comparator<? super T> secondaryItemComparator) {
		this.collator = Collator.getInstance(localStrings.getLocale());
		this.collator.setStrength(Collator.SECONDARY);
		this.collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
		this.sortTitlePrefixes = localStrings.getSortTitlePrefixes();
		this.defaultTitle = defaultTitle;
		this.secondaryItemComparator = secondaryItemComparator;
	}

	@Override
	public int compare(T o1, T o2) {
		int result = collator.compare(sortTitle(o1), sortTitle(o2));
		if (result == 0 && secondaryItemComparator != null) {
			result = secondaryItemComparator.compare(o1, o2);
		}
		return result;
	}

	String calculateSortTitle(T titled) {
		String title = titled.getTitle() == null ? defaultTitle : titled.getTitle();
		for (String prefix : sortTitlePrefixes) {
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

	public String sortTitle(T titled) {
		String sortTitle = sortTitles.get(titled);
		if (sortTitle == null) {
			sortTitles.put(titled, sortTitle = calculateSortTitle(titled));
		}
		return sortTitle;
	}
}
