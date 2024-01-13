package hk.edu.hkbu.comp;

import hk.edu.hkbu.comp.tables.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;



@Slf4j
public class DataScraper {
    final String DATA_FILE_NAME = "data_table.ser";// The file which save the loaded data
    // Create two var to control the table size when read the webpages
    final int U = 200; // 备选网站一共10个，不断更新
    private int V = 1200; //总共收集的网站数量
    private int N_THREADS = 10; //一共10个线程

    // seed URL
    private String seedUrl = "https://ojs.aaai.org/index.php/AAAI/issue/archive";

    private String pattern3 = "^https://ojs\\.aaai\\.org/index\\.php/AAAI/issue/view/\\d+$"; // 目标子链接格式
    private String pattern = "<h1\\s+class=\"page_title\">\\s*([\\s\\S]*?)\\s*</h1>"; // 标题格式

    private String pattern_ab = "<section\\s+class=\"item abstract\">\\s*<h2 class=\"label\">Abstract</h2>\\s*([\\s\\S]*?)\\s*</section>";//ab格式

    private String pattern_key = "<section\\s+class=\"item keywords\">\\s*<h2 class=\"label\">\\s*Keywords:\\s*</h2>\\s*<span class=\"value\">\\s*([\\s\\S]*?)\\s*</span>\\s*</section>";//关键词格式

    private String pattern_y = "<section\\s+class=\"sub_item\">\\s*<h2 class=\"label\">\\s*Published\\s*</h2>\\s*<div class=\"value\">\\s*<span>(\\d{4}-\\d{2}-\\d{2})</span>\\s*</div>\\s*</section>";

    public boolean haveSer = serFileExists();

    private static final String CSV_FILENAME = "collectedData.csv";

    final boolean webFilter = true; // 是否过滤网站

    public static final boolean stem = true; //是否采用stem

    public int webid = 0;

    private int userId = -1;

    // URL means the url need to be read
    private URL urls = new URL();

    // PURL means saved urls
    private PURL purls = new PURL();

    private DataTable dataTable = new DataTable(); // save all key info.(keyword title url) of the website

    private final Object lockObj = new Object(); // lock

    public static int convertUrlToUniqueId(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(url.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            return no.intValue();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Integer> getWebIdsForUser(String filePath, int userId) {
        List<Integer> webIds = new ArrayList<>();
        try (FileInputStream file = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                Cell userIdCell = row.getCell(0);
                Cell webIdCell = row.getCell(1);

                if (userIdCell.getCellType() == CellType.NUMERIC &&
                        userIdCell.getNumericCellValue() == userId) {

                    if (webIdCell != null && webIdCell.getCellType() == CellType.NUMERIC) {
                        webIds.add((int) webIdCell.getNumericCellValue());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return webIds;
    }

    public Set<String> getKeywordsForWebIds(List<Integer> webIds, int webIdIndex, int keywordIndex) {
        Set<String> keywords = new HashSet<>();
        try {
            List<String> allLines = Files.readAllLines(Paths.get(CSV_FILENAME));

            for (String line : allLines.subList(1, allLines.size())) { // 跳过标题行
                String[] values = line.split(","); // 假设CSV使用逗号分隔
                if (values.length > webIdIndex && values.length > keywordIndex) {
                    int currentWebId = Integer.parseInt(values[webIdIndex].trim());
                    if (webIds.contains(currentWebId)) {
                        String[] words = values[keywordIndex].split(",| And |and");
                        keywords.addAll(Arrays.asList(words));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return keywords;
    }


    public DataScraper(int V,
                       int nThreads,
                       String seedUrl,
                       String pattern3,
                       String pattern,
                       String pattern_ab,
                       String pattern_key,
                       String pattern_y,
                       int userId) {
        this.userId = userId;
        if(pattern_y!=null){
            this.pattern_y = pattern_y;
        }

        if(pattern_key!=null){
            this.pattern_key = pattern_key;
        }

        if(pattern_ab!=null){
            this.pattern_ab = pattern_ab;
        }

        if(pattern!=null){
            this.pattern = pattern;
        }
        if(pattern3!=null){
            this.pattern3 = pattern3;
        }
        if(seedUrl!=null){
            this.seedUrl = seedUrl;
        }
        if (V > 0) {
            this.V = V;
        }
        if (nThreads > 0) {
            this.N_THREADS = nThreads;
        }
        //checkAndSetWebId();
    }

    // 允许用户在对象创建后设置参数
    public void setParameters(int V,
                              int nThreads,
                              String seedUrl,
                              String pattern3,
                              String pattern,
                              String pattern_ab,
                              String pattern_key,
                              String pattern_y,
                              int userId) {
        this.userId = userId;
        if(pattern_key!=null){
            this.pattern_key = pattern_key;
        }

        if(pattern_y!=null){
            this.pattern_y = pattern_y;
        }

        if(pattern_ab!=null){
            this.pattern_ab = pattern_ab;
        }

        if(pattern!=null){
            this.pattern = pattern;
        }

        if(pattern3!=null){
            this.pattern3 = pattern3;
        }

        if(seedUrl!=null){
            this.seedUrl = seedUrl;
        }

        if (V > 0) {
            this.V = V;
        }
        if (nThreads > 0) {
            this.N_THREADS = nThreads;
        }
    }


    public DataTable readDataTable() {
        DataTable dataTable = null;
        File serFile = new File(DATA_FILE_NAME);
        if (serFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(serFile))) {
                dataTable = (DataTable) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error reading from SER file: " + e.getMessage());
            }
        }
        return dataTable;
    }

    public void appendToSerFile(DataTable newDataTable) {
        DataTable existingDataTable = readDataTable();
        if (existingDataTable != null) {
            // 合并现有数据和新数据
            existingDataTable.merge(newDataTable);
        } else {
            existingDataTable = newDataTable;
        }

        // 现在序列化更新后的 DataTable 对象
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE_NAME))) {
            oos.writeObject(existingDataTable);
        } catch (IOException e) {
            System.err.println("Error writing to SER file: " + e.getMessage());
        }
    }


    public DataScraper() {
        checkAndSetWebId();
    }

    public boolean serFileExists() {
        File serFile = new File(DATA_FILE_NAME);
        return serFile.exists();
    }

    private void checkAndSetWebId() {
        File csvFile = new File(CSV_FILENAME);
        //PURL purl = new PURL();

        if (csvFile.exists()) {
            try {
                List<String> allLines = Files.readAllLines(Paths.get(CSV_FILENAME));
                //System.out.println(allLines);
                webid = allLines.size() - 1; // 减去标题行
                V = V + allLines.size() - 1;

                // 提取currUrl并添加到purls
                for (String line : allLines.subList(1, allLines.size())) {
                    // 使用正则表达式来分割，忽略被引号包围的逗号
                    String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                    if (values.length > 1) {
                        // 移除可能的引号
                        String url = values[1].trim().replace("\"", "");
                        purls.add(url);
                    }
                }
                //System.out.println(purls.size());
            } catch (IOException e) {
                System.out.println("Error");
                //e.printStackTrace();
                webid = 0;
            }
        } else {
            webid = 0;
        }
    }

    public void run() throws IOException, InterruptedException {
        checkAndSetWebId();
        // use the functions in m
        MyParserCallback m = new MyParserCallback();

        // add the first url
        urls.add(seedUrl);

        // start save the data
        Function<CountDownLatch, Runnable> processWeb = (CountDownLatch latch) -> {
            return () -> {
                while (true) {
                    String currUrl = "";
                    // Critical section 1 (Get an url for processing)
                    synchronized (lockObj) {

                        if (urls.size() > 0) {
                            //get the first url in the urls
                            currUrl = urls.remove(0);
                        } else {
                            // multiple threads
                            try {
                                for (int i = 0; i < 10; i++) {
                                    log.warn("Thread {} is waiting for new URL ({}/{})",
                                            Thread.currentThread().getName(), i + 1, 10);
                                    lockObj.wait(1000);
                                    if (urls.size() != 0) {
                                        break;
                                    }
                                }
                                if (urls.size() == 0) {
                                    // if there is no url in urls, break the current thread
                                    log.warn("Thread {} breaks", Thread.currentThread().getName());
                                    break;
                                } else {
                                    log.warn("Thread {} continues", Thread.currentThread().getName());
                                    continue;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // Critical section 1 end

                    // content里面储存的是一个网页的所有信息
                    String content = m.loadWebPage(currUrl);

                    // 找子链接
                    String pattern2 = "<\\s*a\\s*[^>]*href\\s*=\\s*\"((http|www)[^\\\\\"]*)\""; // 全部子链接
                    Pattern r2 = Pattern.compile(pattern2);
                    Matcher m2 = r2.matcher(content);

                    // 储存子链接
                    while (m2.find()) {
                        String newUrl = m2.group(1);


                        Pattern r3 = Pattern.compile(pattern3);
                        Matcher m3 = r3.matcher(newUrl);

                        String pattern4 = "^https://ojs\\.aaai\\.org/index\\.php/AAAI/article/view/\\d+$";
                        Pattern r4 = Pattern.compile(pattern4);
                        Matcher m4 = r4.matcher(newUrl);

                        if (!currUrl.equals(newUrl) && !urls.contains(newUrl) && !purls.contains(newUrl)) {
                            if (urls.size() < U) {
                                if (m3.find() || m4.find()) {
                                    urls.add(newUrl);
                                }
                            }
                        }
                    }

                    //对这个网站进行过滤，如果是目标网络则留下，不是则直接跳过
                    if (webFilter) {
                        if (!m.goodweb(content, currUrl)) {
                            continue;
                        }
                    }

                    //提取标题
                    String title = " ";
                    Pattern r = Pattern.compile(pattern);
                    Matcher m1 = r.matcher(content);


                    if (m1.find()) {
                        title = m1.group(1).replace("\n", "");
                        title = title.replace("\t", "");
                        title = title.replaceAll("\r", "");
                    }


                    // 提取摘要内容
                    String ab = " ";

                    Pattern r_ab = Pattern.compile(pattern_ab);
                    Matcher m_ab = r_ab.matcher(content);


                    if (m_ab.find()) {
                        ab = m_ab.group(1).trim();
                    }

                    //提取关键词
                    String keywords = " ";
                    Pattern r_key = Pattern.compile(pattern_key);
                    Matcher m_key = r_key.matcher(content);


                    if (m_key.find()) {
                        keywords = m_key.group(1).trim();
                    }

                    //提取publish year
                    String year = "";
                    Pattern r_y = Pattern.compile(pattern_y);
                    Matcher m_y = r_y.matcher(content);


                    if (m_y.find()) {
                        year = m_y.group(1).trim();
                        year = year.split("-")[0];
                    }

                    //提取网页内容，拆解后分析ketwords,指向链接
                    String text = "";
                    try {
                        text = m.loadPlainText(content);
                    } catch (IOException e) {
                        log.error("Failed to parse page");
                        e.printStackTrace();
                    }
                    List<String> cleantext = m.extraKey(text);

                    webid = webid + convertUrlToUniqueId(currUrl);

                    String id = Integer.toString(webid);
                    PageInfo pageInfo = new PageInfo(id, currUrl, title, year, ab, keywords);

                    // Critical section 2
                    synchronized (lockObj) {
                        // if there are enough urls, stop gather the data
                        if (purls.size() >= V) {
                            break;
                        }

                        // let keyword -> (title,url)
                        // to avoid restore the webpages over and over again
                        if(userId==-1){
                            for (String keyword : cleantext) {
                                dataTable.add(keyword, pageInfo);
                            }

                            // load the url into purl table
                            if (!purls.contains(currUrl)) {
                                purls.add(currUrl);
                                saveToCSV(pageInfo);
                                webid = webid + 1;
                            }
                        }
                        else{
                            System.out.println("hello world");
                            List<Integer> likeWebIds = getWebIdsForUser("likeActivity.xlsx", userId);
                            List<Integer> starWebIds = getWebIdsForUser("starActivity.xlsx", userId);
                            List<Integer> userWebIds = getWebIdsForUser("userActivity.xlsx", userId);

                            List<Integer> uniqueLikeWebIds = new ArrayList<>(new HashSet<>(likeWebIds));
                            List<Integer> uniqueStarWebIds = new ArrayList<>(new HashSet<>(starWebIds));
                            List<Integer> uniqueUserWebIds = new ArrayList<>(new HashSet<>(userWebIds));

                            Set<String> likeKeywords = getKeywordsForWebIds(likeWebIds, 1, 4); // 假设Web ID是第二列，关键词是第五列
                            Set<String> starKeywords = getKeywordsForWebIds(starWebIds, 1, 4);
                            Set<String> userKeywords = getKeywordsForWebIds(userWebIds, 1, 4);
                            
                        }


                        //输出链接数量
                        log.info("The number websites in the url table: {}", urls.size());
                        log.info("The number of identified websites: {}", purls.size());
                        lockObj.notifyAll();
                    }
                    // Critical section 2 end
                }
                // know how many job do thread has
                latch.countDown();
                log.info("Job finished. No. of remaining jobs: {}", latch.getCount());
            };
        };

        ExecutorService es = Executors.newFixedThreadPool(N_THREADS);
        CountDownLatch latch = new CountDownLatch(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            es.execute(processWeb.apply(latch));
        }
        latch.await();
        es.shutdown();

        // oos is used to save the object (keyword -> (title,url))
        if(haveSer){
            appendToSerFile(dataTable);
            log.info("Data updated");
        }
        else {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE_NAME));
            oos.writeObject(dataTable);
            oos.flush();
            oos.close();
            log.info("Data saved");
        }
    }

    public static void saveToCSV(PageInfo pageInfo) {
        File csvFile = new File(CSV_FILENAME);
        boolean isNewFile = !csvFile.exists();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile, true))) {
            if (isNewFile) {
                // Write the header only if the file is new
                bw.write("id,currUrl,title,year,keywords,ab\n");
            }

            // Write the data
            bw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    pageInfo.getId(),
                    pageInfo.getUrl(),
                    pageInfo.getTitle(),
                    pageInfo.getYear(),
                    pageInfo.getKeywords(),
                    pageInfo.getAb()));

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Set default values
        int V = 1200; // Default number of websites to collect
        int nThreads = 10; // Default number of threads
        String seedUrl = "https://ojs.aaai.org/index.php/AAAI/issue/archive"; // Default seed URL
        String pattern3 = "^https://ojs\\.aaai\\.org/index\\.php/AAAI/issue/view/\\d+$"; // Default pattern for issue URLs
        String pattern = "<h1\\s+class=\"page_title\">\\s*([\\s\\S]*?)\\s*</h1>"; // Default pattern for page titles
        String pattern_ab = "<section\\s+class=\"item abstract\">\\s*<h2 class=\"label\">Abstract</h2>\\s*([\\s\\S]*?)\\s*</section>"; // Default pattern for abstracts
        String pattern_key = "<section\\s+class=\"item keywords\">\\s*<h2 class=\"label\">\\s*Keywords:\\s*</h2>\\s*<span class=\"value\">\\s*([\\s\\S]*?)\\s*</span>\\s*</section>"; // Default pattern for keywords
        String pattern_y = "<section\\s+class=\"sub_item\">\\s*<h2 class=\"label\">\\s*Published\\s*</h2>\\s*<div class=\"value\">\\s*<span>(\\d{4}-\\d{2}-\\d{2})</span>\\s*</div>\\s*</section>"; // Default pattern for publication year
        int userId = -1;

        // Check for command-line arguments and override defaults if present
        if (args.length > 0) {
            V = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            nThreads = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            seedUrl = args[2];
        }
        if (args.length > 3) {
            pattern3 = args[3];
        }
        if (args.length > 4) {
            pattern = args[4];
        }
        if (args.length > 5) {
            pattern_ab = args[5];
        }
        if (args.length > 6) {
            pattern_key = args[6];
        }
        if (args.length > 7) {
            pattern_y = args[7];
        }
        if (args.length > 8) {
            userId = Integer.parseInt(args[8]);
        }

        // Initialize the DataScraper with the provided or default values
        DataScraper scraper = new DataScraper(V, nThreads, seedUrl, pattern3, pattern, pattern_ab, pattern_key, pattern_y,userId);

        // Run the scraper
        scraper.run();
    }

}
