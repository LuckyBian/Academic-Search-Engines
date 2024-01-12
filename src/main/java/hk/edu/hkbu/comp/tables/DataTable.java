package hk.edu.hkbu.comp.tables;

import lombok.Getter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.*;
import java.util.stream.Collectors;

public class DataTable implements Serializable {
    @Getter
    private Map<String, Set<PageInfo>> index = new HashMap<>();
    public void add(String key, PageInfo page) {
        Set<PageInfo> set = index.get(key);
        if (set == null) {
            set = new HashSet<>();
            index.put(key, set);
        }
        set.add(page);
    }
    public Set<PageInfo> search(String keyword) {
        return index.getOrDefault(keyword, new HashSet<>());
    }

    public void merge(DataTable other) {
        for (String key : other.index.keySet()) {
            Set<PageInfo> otherSet = other.index.get(key);
            Set<PageInfo> thisSet = this.index.get(key);
            if (thisSet == null) {
                this.index.put(key, otherSet);
            } else {
                thisSet.addAll(otherSet);
            }
        }
    }


}
