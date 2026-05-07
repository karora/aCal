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

    AcalProperty get() {
        switch ( propertyList.size() ) {
            case 0: return null;
            case 1: return propertyList.get(0);
            default:
                throw new RuntimeException("Attempt to getProperty('"+propertyName+"') but property is multiple.");
        }
    }

    AcalProperty get(int index) {
        return propertyList.get(index);
    }

    void add(AcalProperty property) {
        propertyList.add(property);
    }

    @Override
    public Iterator iterator() {
        return propertyList.iterator();
    }
}
