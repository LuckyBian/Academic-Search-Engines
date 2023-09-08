package hk.edu.hkbu.comp;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import hk.edu.hkbu.comp.tables.DataTable;
import hk.edu.hkbu.comp.tables.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.tartarus.snowball.ext.englishStemmer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

@Slf4j
@Controller
public class MyController {
    //if there is no mapping or type something wrong，go to index.html
    final String DATA_FILE_NAME = "data_table.ser";
    DataTable dataTable;

    public MyController() throws IOException, InterruptedException, ClassNotFoundException {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(DATA_FILE_NAME));
            dataTable = (DataTable) ois.readObject();
            log.info("Using previously scraped data in {}", DATA_FILE_NAME);
        } catch (Exception e) {
            log.warn("{}", e.getMessage());
            log.warn("Data file not found or corrupted. Re-scraping data...");
            new DataScraper().run();
            ois = new ObjectInputStream(new FileInputStream(DATA_FILE_NAME));
            dataTable = (DataTable) ois.readObject();
        }
    }

    //@ResponseBody
    @RequestMapping("/search")
    public String search(HttpServletRequest request,
                       String query,
                       String scope,
                       HttpServletResponse response,
                       @RequestParam(name = "title", required = false)
                               String title,
                       @RequestParam(name = "url", required = false)
                               String url,
                       Model model) throws IOException {
        //返回搜索结果，需要额外方法进行过滤
        if (query.equals("")) {
            return "redirect:";
        }

        if (scope != null && scope.equals("URL")) {
            if (query.matches("^[a-z0-9]+://.+")) {
                return "redirect:" + query;
            } else {
                return "redirect:";
            }
        } else {
            String[] words = query.split("[\\p{Punct}\\s+]");
            int wordNumber = words.length;

            if (wordNumber == 1) {

                if(DataScraper.stem){
                    words[0] = stem(words[0]);
                }

                words[0] = words[0].toLowerCase();

                Set<PageInfo> resultSet = onesearch(words[0]);
                request.setAttribute("resultSet", resultSet);
                return "index.html";
            }
            else if (wordNumber == 2) {
                if(DataScraper.stem){
                    words[0] = stem(words[0]);
                    words[1] = stem(words[1]);
                }
                words[0] = words[0].toLowerCase();
                words[1] = words[1].toLowerCase();

                if (query.matches("[A-z]+\\+[A-z]+")) {
                    Set<PageInfo> resultSet = andsearch(words[0], words[1]);
                    request.setAttribute("resultSet", resultSet);
                    return "index.html";
                } else if (query.matches("[A-z]+\\![A-z]+")) {
                    Set<PageInfo> resultSet = orsearch(words[0], words[1]);
                    request.setAttribute("resultSet", resultSet);
                    return "index.html";
                } else if (query.matches("[A-z]+-[A-z]+")) {
                    Set<PageInfo> resultSet = notsearch(words[0], words[1]);
                    request.setAttribute("resultSet", resultSet);
                    return "index.html";
                } else {
                    return "redirect:";
                }
            } else {
                return "redirect:";
            }
        }
    }

    @GetMapping(value = "/raw-data", produces = "application/json")
    @ResponseBody
    public Map<String, Set<PageInfo>> rawData() {
        return dataTable.getIndex();
    }

    public Set<PageInfo> onesearch(String text){
        return dataTable.search(text);
    }

    public Set<PageInfo> andsearch(String text1,String text2){
        Set<PageInfo> result1 = dataTable.search(text1);
        Set<PageInfo> result2 = dataTable.search(text2);
        Set<PageInfo> resultSet = new HashSet<>(result1);
        resultSet.retainAll(result2);
        return resultSet;
    }

    public Set<PageInfo> notsearch(String text1,String text2){
        Set<PageInfo> result1 = dataTable.search(text1);
        Set<PageInfo> result2 = dataTable.search(text2);
        Set<PageInfo> resultSet = new HashSet<>(result1);
        resultSet.removeAll(result2);
        return resultSet;
    }

    public Set<PageInfo> orsearch(String text1,String text2){
        Set<PageInfo> result1 = dataTable.search(text1);
        Set<PageInfo> result2 = dataTable.search(text2);
        Set<PageInfo> resultSet = new HashSet<>(result1);
        resultSet.addAll(result2);
        return resultSet;
    }

    public static String stem(String text){
        englishStemmer stemmer = new englishStemmer();
        stemmer.setCurrent(text);
        return stemmer.getCurrent();
    }

}
