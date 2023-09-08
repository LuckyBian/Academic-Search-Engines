package hk.edu.hkbu.comp;

import org.tartarus.snowball.ext.englishStemmer;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MyParserCallback extends HTMLEditorKit.ParserCallback {
    public String content = "";

    String loadPlainText(String html) throws IOException {
        MyParserCallback callback = new MyParserCallback();
        ParserDelegator parser = new ParserDelegator();

        InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        parser.parse(reader, callback, true);

        return callback.content;
    }

    public List<String> extraKey(String text){

        // divide the keyword by puncture
        String[] result = text.split("[\\p{Punct}\\s]+");
        // create a list to save the keywords
        List<String> lastversion = new ArrayList<>();

        // create the blacklist to delete the stop word
        String[] blabklist = new String[]{"a","an,the","do","does","did","has","have",
        "had","is","am","are","was","were","be","being","been","may","must","might",
        "should","could","would","shall","will","can","ought","when","why","how","all",
        "another","any","anybody","anyone","anything","as","aught","both","each","other",
        "either","enough","everybody","everyone","everything","few","he","her","hers","herself",
        "him","himself","his","I","idem","it","its","itself","many","me","mine","most","my", "myself",
        "naught", "neither","nobody","none","nothing","nought","another","other","others","ought",
        "our","ours","ourself","ourselves","several","she","some","somebody","someone","something",
        "somewhat","such","suchlike","that","thee","their","theirs","theirself","theirselves",
        "them","themself","themselves","there","these","they","thine","this","those","thou",
        "thy","thyself","us","we","what","whatever","whatnot","whatsoever","whence","where",
        "whereby","wherefrom","wherein","whereinto","whereof","whereon","wherever","wheresoever",
        "whereto","whereunto","wherewith","wherewithal","whether","which","whichever","whichsoever",
        "who","whoever","whom","whomever","whomso","whomsoever","whose","whosever","whosesoever","whoso",
        "whosoever","ye","yon","yonder","you","your","yours","yourself","yourselves","aboard",
        "about","above","across","after","against","along","amid","among","around","as","at","before",
        "behind","below","beneath","beside","between","beyond","but","by","concerning","considering",
        "despite","down","during","except","following","for","from","in","inside","into",
        "like","minus","near","next","of","off","on","onto","opposite","out","outside","over",
        "past","per","plus","regarding","round","save","since","than","through","to",
        "toward","under","underneath","unlike","until","up","upon","versus","via","with","within","without"};

        for(int i = 0; i < result.length; i++){
            // get the lowercase keyword
            result[i] = result[i].toLowerCase();
            // if the keyword is letter, get the stem of the keyword
            if(is_alpha(result[i])){

                if(DataScraper.stem){
                    englishStemmer stemmer = new englishStemmer();
                    stemmer.setCurrent(result[i]);
                    result[i] = stemmer.getCurrent();
                }

                // if this keyword in the blacklist, delete it directly
                if(Arrays.asList(blabklist).contains(result[i])){
                    continue;
                }
                // if the key word is not in the keyword list, add it
                if(!lastversion.contains(result[i])){
                    lastversion.add(result[i]);
                }
            }
        }
        return lastversion;
    }

    String loadWebPage(String urlString) {
        byte[] buffer = new byte[1024];
        StringBuilder content = new StringBuilder();

        try {
            URL url = new URL(urlString);
            InputStream in = url.openStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String line;
            while((line = br.readLine()) != null) {
                content.append(line);
            }

        } catch (IOException e) {
            content = new StringBuilder("<h1>Unable to download the page</h1>" + urlString);
        }

        return content.toString();
    }

    public boolean is_alpha(String str) {
        if(str==null) return false;
        return str.matches("[a-zA-Z]+");
    }


    public Boolean goodweb(String content){
        String pattern = "<title>([\\s\\S]*?)</title>";
        Pattern r = Pattern.compile(pattern);
        Matcher m1 = r.matcher(content);
        String title = new String();

        if(m1.find()){
            title = m1.group(1).replace("\n","");
            title = title.replace("\t","");
            title = title.replaceAll("\r","");
            title = title.replaceAll("\\p{Punct}", "");
        }
        else{
            return false;
        }

        Pattern p = Pattern.compile("[\u4e00-\u9fa5]");
        Matcher m = p.matcher(title);

        if(m.find()){
            return false;
        }

        String[] words = title.split(" ");

        return true;
    }

    @Override
    public void handleText(char[] data, int pos) {
        content += " " + new String(data);
    }
}

