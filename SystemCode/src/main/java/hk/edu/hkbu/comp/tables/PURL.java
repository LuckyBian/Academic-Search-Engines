package hk.edu.hkbu.comp.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PURL {
    private List<String> purls = Collections.synchronizedList(new ArrayList<String>());

    public int size() {
        return purls.size();
    }

    public boolean contains(String s) {
        return purls.contains(s);
    }

    public boolean add(String s) {
        return purls.add(s);
    }
}
