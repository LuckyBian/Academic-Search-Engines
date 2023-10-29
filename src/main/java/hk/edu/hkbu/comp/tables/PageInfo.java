package hk.edu.hkbu.comp.tables;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class PageInfo implements Serializable {
    private String id;
    private String url;
    private String title;
    private String year;
    private String ab;
    private String keywords;
}

