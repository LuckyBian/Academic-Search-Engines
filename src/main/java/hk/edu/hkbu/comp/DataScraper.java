package hk.edu.hkbu.comp;

import hk.edu.hkbu.comp.tables.*;
import lombok.extern.slf4j.Slf4j;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DataScraper {
    final String DATA_FILE_NAME = "data_table.ser";// The file which save the loaded data
    // Create two var to control the table size when read the webpages
    final int U = 10;
    final int V = 800;
    final int N_THREADS = 10;

    final boolean webFilter = true;

    public static final boolean stem = false;

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
        String seedUrl = "https://www.comp.hkbu.edu.hk/~xkliao/comp4047/SeedPage.html";

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
                        }
                        else {
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
                                }
                                else {
                                    log.warn("Thread {} continues", Thread.currentThread().getName());
                                    continue;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // Critical section 1 end

                    // Non-Critical section (Network IO + Parsing)

                    // Load the webpage
                    // all the info of a html file will be saved in content
                    String content = m.loadWebPage(currUrl);

                    //check the title length and language of the website
                    // to avoid the messy code and Chinese websites
                    if(webFilter){
                        if (!m.goodweb(content)) {
                            continue;
                        }
                    }

                    //extract the title from website
                    String title = "";
                    String pattern = "<title>([\\s\\S]*?)</title>";
                    Pattern r = Pattern.compile(pattern);
                    Matcher m1 = r.matcher(content);
                    if (m1.find()) {
                        title = m1.group(1).replace("\n", "");
                        title = title.replace("\t", "");
                        title = title.replaceAll("\r", "");
                        //title = title.replaceAll("\\p{Punct}", "");
                    }

                    //extract the content of the website
                    String text = "";
                    try {
                        text = m.loadPlainText(content);
                    } catch (IOException e) {
                        log.error("Failed to parse page");
                        e.printStackTrace();
                    }

                    //extra the keywords from the webpage content
                    List<String> cleantext = m.extraKey(text);
                    // Build PageInfo
                    PageInfo pageInfo = new PageInfo(currUrl, title);
                    // Non-Critical section end
                    // now we have keyword and (title,url)

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

                        // get all urls from a webpage
                        String pattern2 = "<\\s*a\\s*[^>]*href\\s*=\\s*\"((http|www)[^\\\\\"]*)\"";
                        Pattern r2 = Pattern.compile(pattern2);
                        Matcher m2 = r2.matcher(content);

                        // store the urls to urls table
                        while (m2.find()) {
                            String newUrl = m2.group(1);
                            if (!currUrl.equals(newUrl) && !urls.contains(newUrl) && !purls.contains(newUrl)) {
                                if (urls.size() < U) {
                                    urls.add(newUrl);
                                }
                            }
                        }

                        // load the url into purl table
                        if (!purls.contains(currUrl)) {
                            purls.add(currUrl);
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

    public static void main(String[] args) throws IOException, InterruptedException {
        new DataScraper().run();
    }
}
