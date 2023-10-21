package hk.edu.hkbu.comp;

import hk.edu.hkbu.comp.tables.*;
import lombok.extern.slf4j.Slf4j;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
@Slf4j
public class DataScraper {
    final String DATA_FILE_NAME = "data_table.ser";// The file which save the loaded data
    // Create two var to control the table size when read the webpages
    final int U = 200; // 备选网站一共10个，不断更新
    final int V = 300; //总共收集的网站数量
    final int N_THREADS = 10; //一共10个线程

    private static final String CSV_FILENAME = "collectedData.csv";

    final boolean webFilter = true; // 是否过滤网站

    public static final boolean stem = true; //是否采用stem

    public int webid = 0;

    // URL means the url need to be read
    private URL urls = new URL();

    // PURL means saved urls
    private PURL purls = new PURL();

    private DataTable dataTable = new DataTable(); // save all key info.(keyword title url) of the website

    private final Object lockObj = new Object(); // lock


    public void run() throws IOException, InterruptedException {
        // use the functions in m
        MyParserCallback m = new MyParserCallback();

        // seed URL
        String seedUrl = "https://ojs.aaai.org/index.php/AAAI/issue/archive";


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
                    String pattern2 = "<\\s*a\\s*[^>]*href\\s*=\\s*\"((http|www)[^\\\\\"]*)\"";
                    Pattern r2 = Pattern.compile(pattern2);
                    Matcher m2 = r2.matcher(content);

                    // 储存子链接
                    while (m2.find()) {
                        String newUrl = m2.group(1);

                        String pattern3 = "^https://ojs\\.aaai\\.org/index\\.php/AAAI/issue/view/\\d+$";
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
                    String pattern = "<h1\\s+class=\"page_title\">\\s*([\\s\\S]*?)\\s*</h1>";
                    Pattern r = Pattern.compile(pattern);
                    Matcher m1 = r.matcher(content);


                    if (m1.find()) {
                        title = m1.group(1).replace("\n", "");
                        title = title.replace("\t", "");
                        title = title.replaceAll("\r", "");
                    }


                    // 提取摘要内容
                    String ab = " ";
                    String pattern_ab = "<section\\s+class=\"item abstract\">\\s*<h2 class=\"label\">Abstract</h2>\\s*([\\s\\S]*?)\\s*</section>";
                    Pattern r_ab = Pattern.compile(pattern_ab);
                    Matcher m_ab = r_ab.matcher(content);


                    if (m_ab.find()) {
                        ab = m_ab.group(1).trim();
                    }

                    //提取关键词
                    String keywords = " ";
                    String pattern_key = "<section\\s+class=\"item keywords\">\\s*<h2 class=\"label\">\\s*Keywords:\\s*</h2>\\s*<span class=\"value\">\\s*([\\s\\S]*?)\\s*</span>\\s*</section>";
                    Pattern r_key = Pattern.compile(pattern_key);
                    Matcher m_key = r_key.matcher(content);


                    if (m_key.find()) {
                        keywords = m_key.group(1).trim();
                    }

                    //提取publish year
                    String year = "";
                    String pattern_y = "<section\\s+class=\"sub_item\">\\s*<h2 class=\"label\">\\s*Published\\s*</h2>\\s*<div class=\"value\">\\s*<span>(\\d{4}-\\d{2}-\\d{2})</span>\\s*</div>\\s*</section>";
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
                        for (String keyword : cleantext) {
                            dataTable.add(keyword, pageInfo);
                        }

                        // load the url into purl table
                        if (!purls.contains(currUrl)) {
                            purls.add(currUrl);
                            saveToCSV(pageInfo);
                            webid = webid + 1;
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

        // oos is used to save the object (keyword -> (title,url))
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE_NAME));
        oos.writeObject(dataTable);
        oos.flush();
        oos.close();
        log.info("Data saved");
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
        new DataScraper().run();
    }
}
