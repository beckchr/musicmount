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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import junit.framework.Assert;

import org.junit.Test;
import org.musicmount.builder.model.Titled;

public class CollectionSectionIndexTest {
	List<Titled> items(String... titles) {
		ArrayList<Titled> items = new ArrayList<>();
		for (final String title : titles) {
			items.add(new Titled() {
				@Override
				public String getTitle() {
					return title;
				}
			});
		}
		return items;
	}
	
	@Test
	public void test() {
		Iterable<Titled> items = items("Miles Davis", "David Bowie", "The Beatles", null, "2Pac",  "Bob Dylan");
		CollectionSectionIndex<Titled> index = new CollectionSectionIndex<Titled>(new LocalStrings(Locale.ENGLISH), items, "Unknown", null);
		Iterator<CollectionSection<Titled>> sections = index.getSections().iterator();
		CollectionSection<Titled> section;
		
		Assert.assertTrue(sections.hasNext());
		section = sections.next();
		Assert.assertEquals("B", section.getTitle());
		Assert.assertEquals(2, section.getItems().size());
		Assert.assertEquals("The Beatles", section.getItems().get(0).getTitle());
		Assert.assertEquals("Bob Dylan", section.getItems().get(1).getTitle());
		Assert.assertTrue(sections.hasNext());
		section = sections.next();
		Assert.assertEquals("D", section.getTitle());
		Assert.assertEquals(1, section.getItems().size());
		Assert.assertEquals("David Bowie", section.getItems().get(0).getTitle());
		Assert.assertTrue(sections.hasNext());
		section = sections.next();
		Assert.assertEquals("M", section.getTitle());
		Assert.assertEquals(1, section.getItems().size());
		Assert.assertEquals("Miles Davis", section.getItems().get(0).getTitle());
		Assert.assertTrue(sections.hasNext());
		section = sections.next();
		Assert.assertEquals("#", section.getTitle());
		Assert.assertEquals(1, section.getItems().size());
		Assert.assertEquals("2Pac", section.getItems().get(0).getTitle());
		Assert.assertTrue(sections.hasNext());
		section = sections.next();
		Assert.assertEquals("?", section.getTitle());
		Assert.assertEquals(1, section.getItems().size());
		Assert.assertNull(section.getItems().get(0).getTitle());
		Assert.assertFalse(sections.hasNext());
	}

	@Test
	public void testSecondary() {
		List<Titled> items = items(null, null, null, null, null);
		// sort descending by hash code
		Collections.sort(items, new Comparator<Titled>() {
			@Override
			public int compare(Titled o1, Titled o2) {
				return Integer.valueOf(o2.hashCode()).compareTo(o1.hashCode());
			}
		});
		// pass secondary comparator which sorts ascending by hash code
		CollectionSectionIndex<Titled> index = new CollectionSectionIndex<Titled>(new LocalStrings(Locale.ENGLISH), items, "Unknown", new Comparator<Titled>() {
			@Override
			public int compare(Titled o1, Titled o2) {
				return Integer.valueOf(o1.hashCode()).compareTo(o2.hashCode());
			}
		});
		Iterator<CollectionSection<Titled>> sections = index.getSections().iterator();
		Assert.assertTrue(sections.hasNext());
		CollectionSection<Titled> section = sections.next();
		
		// should be 1 section "?" with 5 null-titled items, with ascending hash codes
		Assert.assertEquals("?", section.getTitle());
		List<Titled> sortedItems = section.getItems();
		Assert.assertEquals(5, sortedItems.size());
		Assert.assertNull(sortedItems.get(0).getTitle());
		for (int i = 1; i < items.size(); i++) {
			Assert.assertNull(sortedItems.get(i).getTitle());
			Assert.assertTrue(sortedItems.get(i-1).hashCode() <= sortedItems.get(i).hashCode());
		}
		Assert.assertFalse(sections.hasNext());
	}
}
