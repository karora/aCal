package org.davical.acal.davacal;

import java.util.ArrayList;
import java.util.Iterator;


/**
 * AcalPropertySet is here because while <em>most</em> properties are singletons, a few
 * of them can occur multiply.  Consequently this class tries to look like a thin layer
 * through to AcalProperty, it will throw an exception if you try and access a multiple
 * as if it was a singleton.
 *
 * @author karora
 *
 */
public class AcalPropertySet implements Iterable<AcalProperty> {

    private final ArrayList<AcalProperty> propertyList;
    private final String propertyName;

    AcalPropertySet(AcalProperty firstProperty) {
        this.propertyName = firstProperty.getName();
        this.propertyList = new ArrayList<AcalProperty>();
        propertyList.add(firstProperty);
    }

    int size() {
        return propertyList.size();
    }

    /**
     * Returns a single property from the set.  Where the property occurs multiple
     * times the last one is returned; callers needing all of them should iterate
     * or use VComponent.getProperties(PropertyName).
     */
    AcalProperty get() {
        if ( propertyList.isEmpty() ) return null;
        return propertyList.get(propertyList.size() - 1);
    }

    AcalProperty get(int index) {
        return propertyList.get(index);
    }

    void add(AcalProperty property) {
        propertyList.add(property);
    }

    @Override
    public Iterator<AcalProperty> iterator() {
        return propertyList.iterator();
    }
}
