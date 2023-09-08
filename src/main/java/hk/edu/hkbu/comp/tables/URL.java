package hk.edu.hkbu.comp.tables;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
public class URL {
    private List<String> urls = Collections.synchronizedList(new ArrayList<>());

    public boolean add(String url) {
        urls.add(url);
        log.info("URL added: {}", url);
        return true;
    }

    public String get(int index) {
        return urls.get(index);
    }

    public int size() {
        return urls.size();
    }

    public String remove(int index) {
        String url = urls.remove(index);
        log.info("URL removed: {}", url);
        return url;
    }

    public boolean contains(String s) {
        return urls.contains(s);
    }

}
