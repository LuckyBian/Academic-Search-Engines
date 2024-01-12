package hk.edu.hkbu.comp;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import hk.edu.hkbu.comp.tables.DataTable;
import hk.edu.hkbu.comp.tables.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tartarus.snowball.ext.englishStemmer;
import hk.edu.hkbu.comp.DataScraper;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@CrossOrigin(origins = "*")
//@RestController
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

    @PostMapping("/run-data-scraper")
    public ResponseEntity<?> runDataScraper() {
        try {
            // 假设DataScraper有一个可以调用的方法来执行抓取任务
            new DataScraper().run();

            // 返回成功响应
            return ResponseEntity.ok("Data Scraper Executed Successfully");
        } catch (Exception e) {
            // 返回错误响应
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error executing data scraper: " + e.getMessage());
        }
    }

    @GetMapping("/")
    public String displayHomePage(Model model,HttpServletRequest request) throws Exception {
        String currentUserId = getUserIdFromRequest(request);

        Map<String, Map<String, String>> userBasedRecommendations = new HashMap<>();
        Map<String, Map<String, String>> itemBasedRecommendations = new HashMap<>();
        Map<String, Map<String, String>> mix = new HashMap<>();
        List<String> mix1 = getTopWebIds2();

        if (currentUserId != null) {
            List<String> userBasedid = recommendByUserBased(currentUserId);
            userBasedRecommendations = getWebPageDetails(userBasedid);

            List<String> itemBasedId = recommendByItemBased(currentUserId);
            itemBasedRecommendations = getWebPageDetails(itemBasedId);

            List<String> mix2 = recommendByItemBased2(currentUserId);
            Set<String> mix1Set = new HashSet<>(mix1);
            Set<String> mix2Set = new HashSet<>(mix2);
            Set<String> itemBasedIdSet = new HashSet<>(itemBasedId);
            mix1Set.retainAll(mix2Set);
            mix1Set.removeAll(itemBasedIdSet);
            List<String> result = new ArrayList<>(mix1Set);
            result = result.subList(0, Math.min(result.size(), 5));
            //System.out.println(mix1);
            //System.out.println(mix2);
            mix = getWebPageDetails(result);
        }

        model.addAttribute("itemBasedRecommendations", itemBasedRecommendations);
        model.addAttribute("userRecommendations", userBasedRecommendations);
        model.addAttribute("mix", mix);

        List<String> topWebIds = getTopWebIds();
        List<Map<String, String>> latest5Papers = getLatest5Papers();

        Map<String, Map<String, String>> webDetails = getWebPageDetails(topWebIds);
        model.addAttribute("webDetails", webDetails);
        model.addAttribute("latest5Papers", latest5Papers);
        return "index";
    }


    @RequestMapping("/person.html")
    public String personalPage(Model model, HttpServletRequest request) throws IOException {
        String currentUserId = getUserIdFromRequest(request);

        List<Map<String, String>> userHistoryList = getUserWebIdsAndTimes(currentUserId);
        //System.out.println(userHistoryList);
        model.addAttribute("userHistory", userHistoryList);

        List<Map<String, String>> userLikedWebIds = getUserLikedWebIds(currentUserId);
        model.addAttribute("userLiked", userLikedWebIds);

        List<Map<String, String>> useratarIds = getStared(currentUserId);
        model.addAttribute("star", useratarIds);

        return "person.html";
    }



    @RequestMapping("/survey.html")
    public String survey(){
        return "survey.html";
    }

    //@ResponseBody
    @RequestMapping("/search")
    public String search(HttpServletRequest request,
                         @RequestParam(required = false) String query,
                         @RequestParam(name = "startYear", required = false) Integer startYear,
                         @RequestParam(name = "endYear", required = false) Integer endYear,
                         String scope,
                         HttpServletResponse response,
                         @RequestParam(name = "title", required = false) String title,
                         @RequestParam(name = "url", required = false) String url,
                         Model model) throws IOException {

        if (query == null || query.trim().isEmpty()) {
            return "redirect:"; // Redirect to home if the query is null or empty
        }

        request.setAttribute("query", query); // Set query as request attribute

        if (scope != null && scope.equals("URL")) {
            if (query.matches("^[a-z0-9]+://.+")) {
                return "redirect:" + query;
            } else {
                return "redirect:";
            }
        } else {
            int pageNumber;
            try {
                pageNumber = Integer.parseInt(request.getParameter("page"));
            } catch (NumberFormatException e) {
                pageNumber = 1; // Default to first page if no page number is provided
            }

            int itemsPerPage = 5;
            String[] words = query.split("[\\p{Punct}\\s+]");
            Map<PageInfo, Integer> resultMap = new HashMap<>();

            for (String word : words) {
                if (DataScraper.stem) {
                    word = stem(word);
                }
                word = word.toLowerCase();
                Set<PageInfo> currentResults = onesearch(word);
                for (PageInfo pageInfo : currentResults) {
                    resultMap.put(pageInfo, resultMap.getOrDefault(pageInfo, 0) + 1);
                }
            }

            // 将结果按照匹配关键词数量排序
            List<PageInfo> sortedResults = resultMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.<PageInfo, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            Set<PageInfo> allResults = new LinkedHashSet<>(sortedResults);


            if(startYear != null && endYear != null) {
                allResults = allResults.stream()
                        .filter(pageInfo -> Integer.parseInt(pageInfo.getYear()) >= startYear && Integer.parseInt(pageInfo.getYear()) <= endYear)
                        .collect(Collectors.toSet());
            }

            Map<String, Integer> sortedTopKeywords = getKeywordCounts(allResults);

            int totalPages = (int) Math.ceil((double) allResults.size() / itemsPerPage);

            if(allResults.size() > itemsPerPage) {
                // Get the subset of results for the given page number
                Set<PageInfo> paginatedResults = new HashSet<>(allResults.stream()
                        .skip((pageNumber - 1) * itemsPerPage)
                        .limit(itemsPerPage)
                        .collect(Collectors.toSet()));
                request.setAttribute("resultSet", paginatedResults);
            } else {
                request.setAttribute("resultSet", allResults);
            }

            request.setAttribute("totalPages", totalPages);
            request.setAttribute("currentPage", pageNumber);

            model.addAttribute("keywordCounts", sortedTopKeywords);

            if (words.length > 0) {
                if(totalPages==0){
                    return "redirect:";
                }
                return "web.html";
            } else {
                return "redirect:";
            }
        }
    }

    @RestController
    @RequestMapping("/api")
    public class KeywordController {

        // ... (your search method and the getKeywordCounts method) ...

        @GetMapping("/getKeywordCounts")
        public Map<String, Integer> keywordCountsAPI(@RequestParam(required = false) String query) {
            // You can call the logic you have in the search method to get 'allResults'
            // Or, if necessary, create a common method to get 'allResults' based on the query.
            // For now, I'll just call the code you provided in the search method to get 'allResults'.
            String[] words = query.split("[\\p{Punct}\\s+]");
            Set<PageInfo> allResults = new HashSet<>();
            for (String word : words) {
                if (DataScraper.stem) {
                    word = stem(word);
                }
                word = word.toLowerCase();
                allResults.addAll(onesearch(word));
            }
            return getKeywordCounts(allResults);
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

    public static String stem(String text){
        englishStemmer stemmer = new englishStemmer();
        stemmer.setCurrent(text);
        return stemmer.getCurrent();
    }

    public List<String> getTopWebIds() throws IOException {
        FileInputStream fis = new FileInputStream("userActivity.xlsx");
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        Iterator<Row> iterator = sheet.iterator();

        Map<String, Integer> webIdCount = new HashMap<>();

        int rowIndex = 0;  // 添加一个行索引计数器

        while (iterator.hasNext()) {
            Row currentRow = iterator.next();

            if (rowIndex == 0) {  // 如果是第一行，跳过处理
                rowIndex++;  // 增加行索引
                continue;  // 继续到下一次循环
            }

            String webId = currentRow.getCell(1).getStringCellValue();
            webIdCount.put(webId, webIdCount.getOrDefault(webId, 0) + 1);
            rowIndex++;  // 增加行索引
        }

        List<String> topWebIds = webIdCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return topWebIds;
    }


    public List<String> getTopWebIds2() throws IOException {
        FileInputStream fis = new FileInputStream("userActivity.xlsx");
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        Iterator<Row> iterator = sheet.iterator();

        Map<String, Integer> webIdCount = new HashMap<>();

        int rowIndex = 0;  // 添加一个行索引计数器

        while (iterator.hasNext()) {
            Row currentRow = iterator.next();

            if (rowIndex == 0) {  // 如果是第一行，跳过处理
                rowIndex++;  // 增加行索引
                continue;  // 继续到下一次循环
            }

            String webId = currentRow.getCell(1).getStringCellValue();
            webIdCount.put(webId, webIdCount.getOrDefault(webId, 0) + 1);
            rowIndex++;  // 增加行索引
        }

        List<String> topWebIds = webIdCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return topWebIds;
    }


    public Map<String, Map<String, String>> getWebPageDetails(List<String> topWebIds) throws IOException {
        //System.out.println("topWebIds: " + topWebIds);
        List<String> lines = Files.readAllLines(Paths.get("collectedData.csv"));
        Map<String, Map<String, String>> webDetails = new HashMap<>();

        for (String line : lines) {
            //System.out.println("Processing line: " + line);
            String[] tokens = line.split(",");
            String id = tokens[0].replaceAll("\"", "");
            //System.out.println("Processing ID from CSV: " + tokens[0]);
            //System.out.println("Found ID: " + tokens[0]);
            if (topWebIds.contains(id)) {
                Map<String, String> details = new HashMap<>();
                details.put("id", tokens[0]);
                details.put("currUrl", tokens.length > 1 ? tokens[1] : " ");
                details.put("title", tokens.length > 2 ? tokens[2] : " ");
                details.put("year", tokens.length > 3 ? tokens[3] : " ");
                details.put("keywords", tokens.length > 4 ? tokens[4] : " ");
                details.put("ab", tokens.length > 5 ? tokens[5] : " ");
                webDetails.put(id, details);
            }
        }

        //System.out.println(webDetails);

        return webDetails;
    }


    public List<Map<String, String>> getLatest5Papers() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("collectedData.csv"));

        List<Map<String, String>> allPapers = new ArrayList<>();

        // Skip the first line since it's the header row
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] tokens = line.split(",");

            Map<String, String> paper = new HashMap<>();
            paper.put("id", tokens[0]);
            paper.put("currUrl", tokens[1]);
            paper.put("title", tokens[2]);
            paper.put("year", tokens[3]);
            paper.put("ab", tokens[5]);
            paper.put("keywords", tokens[4]);

            allPapers.add(paper);
        }

        return allPapers.stream()
                .sorted((p1, p2) -> {
                    try {
                        int year1 = Integer.parseInt(p1.get("year").replace("\"", "").trim());
                        int year2 = Integer.parseInt(p2.get("year").replace("\"", "").trim());
                        return Integer.compare(year2, year1);
                    } catch (NumberFormatException e1) {
                        try {
                            Integer.parseInt(p1.get("year").replace("\"", "").trim()); // check p1
                            return -1; // p1 is valid, p2 is not
                        } catch (NumberFormatException e2) {
                            try {
                                Integer.parseInt(p2.get("year").replace("\"", "").trim()); // check p2
                                return 1;  // p2 is valid, p1 is not
                            } catch (NumberFormatException e3) {
                                return 0;  // both are invalid
                            }
                        }
                    }
                })
                .limit(5)
                .collect(Collectors.toList());
    }


    private Map<String, Integer> getKeywordCounts(Set<PageInfo> allResults) {
        Map<String, Integer> keywordCounts = new HashMap<>();
        for (PageInfo pageInfo : allResults) {
            if (pageInfo.getKeywords() != null && !pageInfo.getKeywords().isEmpty()) {
                String[] keywords = pageInfo.getKeywords().split(","); // Assuming keywords are comma-separated.
                for (String keyword : keywords) {
                    keyword = keyword.trim().toLowerCase();
                    keywordCounts.put(keyword, keywordCounts.getOrDefault(keyword, 0) + 1);
                }
            }
        }
        return keywordCounts.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                .limit(20)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public List<String> recommendByUserBased(String currentUserId) throws Exception {
        List<String> recommendations = new ArrayList<>();
        Map<String, Set<String>> userLikes = new HashMap<>();

        // 1. 使用 try-with-resources 结构确保工作簿被正确关闭
        try (Workbook workbook = WorkbookFactory.create(new File("likeActivity.xlsx"))) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                String userId = row.getCell(0).getStringCellValue();
                String webId = row.getCell(1).getStringCellValue();

                userLikes.putIfAbsent(userId, new HashSet<>());
                userLikes.get(userId).add(webId);
            }
        } // 这里Workbook会被自动关闭

        if (userLikes == null || userLikes.isEmpty()) {
            // 可以记录一个错误、抛出一个异常或其他任何适当的操作
            return new ArrayList<>(); // 返回一个空的推荐列表
        }
        String mostSimilarUser = null;
        int maxCommonLikes = 0;

        // 2. 查找与当前用户最相似的用户
        for (Map.Entry<String, Set<String>> entry : userLikes.entrySet()) {
            if (!entry.getKey().equals(currentUserId)) {

                Set<String> currentUserLikes = userLikes.get(currentUserId);
                if (currentUserLikes == null) {
                    // 可以记录一个错误、抛出一个异常或其他任何适当的操作
                    return new ArrayList<>(); // 返回一个空的推荐列表
                }
                Set<String> commonLikes = new HashSet<>(currentUserLikes);
                commonLikes.retainAll(entry.getValue());

                if (commonLikes.size() > maxCommonLikes) {
                    maxCommonLikes = commonLikes.size();
                    mostSimilarUser = entry.getKey();
                }
            }
        }

        // 3. 获取推荐
        if (mostSimilarUser != null) {
            Set<String> likedByCurrent = userLikes.get(currentUserId);
            Set<String> likedByMostSimilar = userLikes.get(mostSimilarUser);

            for (String webId : likedByMostSimilar) {
                if (!likedByCurrent.contains(webId)) {
                    recommendations.add(webId);
                    if (recommendations.size() == 5) {
                        break;
                    }
                }
            }
        }

        return recommendations;
    }


    private String getUserIdFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("userId".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }


    public List<String> recommendByItemBased(String currentUserId) throws Exception {
        List<String> likedWebIds = getLikedWebIdsFromExcel(currentUserId);
        //System.out.println(likedWebIds);
        Map<String, String> allWebKeywords = getAllWebKeywords();

        Set<String> likedKeywords = new HashSet<>();
        //System.out.println(allWebKeywords);  // 打印所有的webId及其关键词
        for (String webId : likedWebIds) {
            //System.out.println("Processing webId: " + webId);
            String keywords = allWebKeywords.get(webId);
            //System.out.println("Keywords for webId " + webId + ": " + keywords);
            if (keywords == null) {
                keywords = " ";  // 或者 keywords = "";
            }
            likedKeywords.addAll(Arrays.asList(keywords.split(",")));
        }
        //System.out.println(likedKeywords);

        Map<String, Integer> webMatchingCount = new HashMap<>();
        for (Map.Entry<String, String> entry : allWebKeywords.entrySet()) {
            String webId = entry.getKey();
            String[] keywords = entry.getValue().split(",");
            for (String keyword : keywords) {
                if (likedKeywords.contains(keyword) && !likedWebIds.contains(webId)) {
                    webMatchingCount.put(webId, webMatchingCount.getOrDefault(webId, 0) + 1);
                }
            }
        }

        List<String> recommendations = webMatchingCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return recommendations;
    }

    public List<String> recommendByItemBased2(String currentUserId) throws Exception {
        List<String> likedWebIds = getLikedWebIdsFromExcel(currentUserId);
        //System.out.println(likedWebIds);
        Map<String, String> allWebKeywords = getAllWebKeywords();

        Set<String> likedKeywords = new HashSet<>();
        //System.out.println(allWebKeywords);  // 打印所有的webId及其关键词
        for (String webId : likedWebIds) {
            //System.out.println("Processing webId: " + webId);
            String keywords = allWebKeywords.get(webId);
            //System.out.println("Keywords for webId " + webId + ": " + keywords);
            if (keywords == null) {
                keywords = " ";  // 或者 keywords = "";
            }
            likedKeywords.addAll(Arrays.asList(keywords.split(",")));
        }
        //System.out.println(likedKeywords);

        Map<String, Integer> webMatchingCount = new HashMap<>();
        for (Map.Entry<String, String> entry : allWebKeywords.entrySet()) {
            String webId = entry.getKey();
            String[] keywords = entry.getValue().split(",");
            for (String keyword : keywords) {
                if (likedKeywords.contains(keyword) && !likedWebIds.contains(webId)) {
                    webMatchingCount.put(webId, webMatchingCount.getOrDefault(webId, 0) + 1);
                }
            }
        }

        List<String> recommendations = webMatchingCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return recommendations;
    }

    public Map<String, String> getAllWebKeywords() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("collectedData.csv"));
        Map<String, String> webKeywords = new HashMap<>();

        for (String line : lines) {
            String[] tokens = line.split(",");
            tokens[0] = tokens[0].replaceAll("\"", "");
            webKeywords.put(tokens[0], tokens[4]);
        }

        return webKeywords;
    }


    public List<String> getLikedWebIdsFromExcel(String userId) throws Exception {
        List<String> likedWebIds = new ArrayList<>();

        FileInputStream fis = new FileInputStream(new File("likeActivity.xlsx"));
        XSSFWorkbook workbook = new XSSFWorkbook(fis);
        XSSFSheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            XSSFRow row = sheet.getRow(i);

            if (row != null) {
                XSSFCell userIdCell = row.getCell(0);
                XSSFCell webIdCell = row.getCell(1);

                if (userIdCell != null && webIdCell != null) {
                    String cellUserId = userIdCell.getStringCellValue();
                    String webId = webIdCell.getStringCellValue();

                    if (userId.equals(cellUserId)) {
                        likedWebIds.add(webId);
                    }
                }
            }
        }
        workbook.close();
        fis.close();
        return likedWebIds;
    }

    public class AuthInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("userId".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                        return true;
                    }
                }
            }
            response.sendRedirect("/");  // 如果没有找到userId的cookie，重定向到首页
            return false;
        }
    }

    @Configuration
    public class WebConfig implements WebMvcConfigurer {

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/person.html");
        }
    }

    public List<Map<String, String>> getUserHistory(String userId) throws IOException {
        FileInputStream fis = new FileInputStream("userActivity.xlsx");
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        Iterator<Row> iterator = sheet.iterator();

        List<String> userWebIds = new ArrayList<>();

        while (iterator.hasNext()) {
            Row currentRow = iterator.next();
            String currentUserId = currentRow.getCell(0).getStringCellValue();
            String webId = currentRow.getCell(1).getStringCellValue();
            if(userId.equals(currentUserId)){
                userWebIds.add(webId);
            }
        }

        return (List<Map<String, String>>) getWebPageDetails(userWebIds);
    }

    public List<Map<String, String>> getUserWebIdsAndTimes(String currentUserId) throws IOException {
        try (FileInputStream fis = new FileInputStream("userActivity.xlsx");
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            List<String> userWebIds = new ArrayList<>();
            Map<String, String> visitTimes = new HashMap<>();

            while (iterator.hasNext()) {
                Row currentRow = iterator.next();
                String userId = currentRow.getCell(0).getStringCellValue();
                userId = userId.replaceAll("\"","");
                currentUserId = currentUserId.replaceAll("\"","");

                // 只处理当前用户的数据
                if (userId.equals(currentUserId)) {
                    String webId = currentRow.getCell(1).getStringCellValue();
                    String visitTime = currentRow.getCell(2).getStringCellValue();

                    userWebIds.add(webId);
                    visitTimes.put(webId, visitTime);
                }
            }
            // 使用getWebPageDetails来获取webId的详细信息
            Map<String, Map<String, String>> webDetails = getWebPageDetails(userWebIds);

            List<Map<String, String>> finalResults = new ArrayList<>();

            for (String webId : userWebIds) {
                webId = webId.replaceAll("\"","");
                //System.out.println(webId);
                Map<String, String> detail = webDetails.get(webId);
                if (detail != null) {
                    Map<String, String> userRecord = new HashMap<>();
                    userRecord.put("webId", detail.get("id"));
                    //System.out.println(detail.get("id"));
                    userRecord.put("link", detail.get("currUrl"));
                    //System.out.println(detail.get("currUrl"));
                    userRecord.put("time", visitTimes.get(webId));
                    finalResults.add(userRecord);
                }
            }

            return finalResults;

        } catch (Exception e) {
            // 发生异常时，返回一个空的List
            return new ArrayList<>();
        }
    }

    public List<Map<String, String>> getUserLikedWebIds(String currentUserId) throws IOException {
        try (FileInputStream fis = new FileInputStream("likeActivity.xlsx");
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            List<String> userLikedWebIds = new ArrayList<>();

            List<Map<String, String>> finalLikedResults = new ArrayList<>();

            while (iterator.hasNext()) {
                Row currentRow = iterator.next();
                String userId = currentRow.getCell(0).getStringCellValue();

                // 只处理当前用户的数据
                if (userId.equals(currentUserId)) {
                    String webId = currentRow.getCell(1).getStringCellValue();
                    webId = webId.replaceAll("\"", "");

                    List<String> id = new ArrayList<>();
                    id.add(webId);

                    Map<String, Map<String, String>> webDetails = getWebPageDetails(id);
                    if (webDetails.get(webId) != null) {
                        Map<String, String> userLikedRecord = new HashMap<>();
                        userLikedRecord.put("webId", webDetails.get(webId).get("id"));
                        userLikedRecord.put("link", webDetails.get(webId).get("currUrl"));
                        finalLikedResults.add(userLikedRecord);
                    }
                }
            }

            return finalLikedResults;

        } catch (Exception e) {
            // 发生异常时，返回一个空的List
            return new ArrayList<>();
        }
    }

    public List<Map<String, String>> getStared(String currentUserId) throws IOException {
        try (FileInputStream fis = new FileInputStream("starActivity.xlsx");
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            List<String> userWebIds = new ArrayList<>();
            Map<String, String> visitTimes = new HashMap<>();

            while (iterator.hasNext()) {
                Row currentRow = iterator.next();
                String userId = currentRow.getCell(0).getStringCellValue();
                userId = userId.replaceAll("\"","");
                currentUserId = currentUserId.replaceAll("\"","");

                // 只处理当前用户的数据
                if (userId.equals(currentUserId)) {
                    String webId = currentRow.getCell(1).getStringCellValue();
                    String ab = currentRow.getCell(2).getStringCellValue();

                    userWebIds.add(webId);
                    visitTimes.put(webId,ab);
                }
            }
            // 使用getWebPageDetails来获取webId的详细信息
            Map<String, Map<String, String>> webDetails = getWebPageDetails(userWebIds);

            List<Map<String, String>> finalResults = new ArrayList<>();

            for (String webId : userWebIds) {
                webId = webId.replaceAll("\"","");
                //System.out.println(webId);
                Map<String, String> detail = webDetails.get(webId);
                if (detail != null) {
                    Map<String, String> userRecord = new HashMap<>();
                    userRecord.put("webId", detail.get("id"));
                    //System.out.println(detail.get("id"));
                    userRecord.put("link", detail.get("currUrl"));
                    //System.out.println(detail.get("currUrl"));
                    String ab = visitTimes.get(webId);
                    ab = ab.replaceAll("BREAK","");
                    ab = ab.replaceAll("\\[","");
                    userRecord.put("ab",ab);
                    finalResults.add(userRecord);
                }
            }

            return finalResults;

        } catch (Exception e) {
            // 发生异常时，返回一个空的List
            return new ArrayList<>();
        }
    }


    public List<String> getAllExistingUserIds() throws IOException {
        List<String> userIds = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream("likeActivity.xlsx");
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            while (iterator.hasNext()) {
                Row currentRow = iterator.next();
                String userId = currentRow.getCell(0).getStringCellValue();
                userIds.add(userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 发生异常时，返回一个空的List
            return new ArrayList<>();
        }
        return userIds;
    }


    @RestController
    @RequestMapping("/api")
    public class UserController {
    }

    @GetMapping("/existingUserIds")
    public ResponseEntity<List<String>> getExistingUserIds() {
        try {
            List<String> userIds = getAllExistingUserIds();
            return ResponseEntity.ok(userIds);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/randomWebsites")
    @ResponseBody
    public List<Map<String, String>> getRandomWebsites() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("collectedData.csv"));
        List<Map<String, String>> allWebsites = new ArrayList<>();
        for (String line : lines) {
            String[] tokens = line.split(",");
            Map<String, String> details = new HashMap<>();
            details.put("id", tokens[0]);
            details.put("currUrl", tokens[1]);
            details.put("title", tokens[2]);
            allWebsites.add(details);
        }
        Collections.shuffle(allWebsites);
        return allWebsites.subList(0, Math.min(allWebsites.size(), 10));
    }

    @PostMapping("/api/saveInterest")
    public ResponseEntity<String> saveUserInterest(@RequestBody InterestRequest interestRequest, HttpServletResponse response) {
        try {
            File file = new File("likeActivity.xlsx");
            Workbook workbook;
            Sheet sheet;
            if (file.exists()) {
                workbook = new XSSFWorkbook(new FileInputStream(file));
                sheet = workbook.getSheetAt(0);
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("Activity");
            }

            for (String websiteId : interestRequest.getInterestedWebsites()) {
                Row newRow = sheet.createRow(sheet.getLastRowNum() + 1);
                newRow.createCell(0).setCellValue(interestRequest.getUserId());
                newRow.createCell(1).setCellValue(websiteId);
            }

            FileOutputStream fileOut = new FileOutputStream("likeActivity.xlsx");
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();

            return ResponseEntity.ok("Successfully saved user interests.");
        } catch (Exception e) {
            log.error("Error saving user interests: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving user interests.");
        }
    }

    public static class InterestRequest {
        private String userId;
        private List<String> interestedWebsites;

        // getters and setters...
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public List<String> getInterestedWebsites() {
            return interestedWebsites;
        }

        public void setInterestedWebsites(List<String> interestedWebsites) {
            this.interestedWebsites = interestedWebsites;
        }
    }

    @ControllerAdvice
    public class GlobalDefaultExceptionHandler {
        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<String> handleNotFound(Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Page not found: " + e.getMessage());
        }
    }

}


