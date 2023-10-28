package hk.edu.hkbu.comp;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.Data;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
public class ActivityController {
    @PostMapping("/saveClickActivity")
    public ResponseEntity<?> saveClickActivity(@RequestBody ClickActivity activity) {
        try {
            // 保存到Excel文件
            saveToExcel(activity);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/removeStar")
    public ResponseEntity<?> removeStar(@RequestBody LikeActivity activity) {
        try {
            // Remove star from Excel
            removeStarFromExcel(activity);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PostMapping("/removeLike")
    public ResponseEntity<?> removeLike(@RequestBody LikeActivity activity) {
        try {
            // Remove like from Excel
            removeLikeFromExcel(activity);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void saveToExcel(ClickActivity activity) throws IOException {
        String filename = "userActivity.xlsx";
        File file = new File(filename);
        Workbook workbook;

        if (!file.exists()) {
            workbook = new XSSFWorkbook();
            // 创建新的sheet
            Sheet sheet = workbook.createSheet("UserActivity");
            // 创建标题行
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("User ID");
            header.createCell(1).setCellValue("Web ID");
            header.createCell(2).setCellValue("Timestamp");  // New header for Timestamp
        } else {
            workbook = new XSSFWorkbook(new FileInputStream(file));
        }

        Sheet sheet = workbook.getSheetAt(0);
        int lastRowNum = sheet.getLastRowNum();
        Row newRow = sheet.createRow(lastRowNum + 1);

        newRow.createCell(0).setCellValue(activity.getUserId());
        newRow.createCell(1).setCellValue(activity.getWebId());
        newRow.createCell(2).setCellValue(activity.getTimestamp());  // Adding the timestamp value

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
    }

    private void saveLikeToExcel(LikeActivity activity) throws IOException {
        String filename = "likeActivity.xlsx";
        File file = new File(filename);
        Workbook workbook;

        if (!file.exists()) {
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("LikeActivity");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("User ID");
            header.createCell(1).setCellValue("Web ID");
        } else {
            workbook = new XSSFWorkbook(new FileInputStream(file));
        }

        Sheet sheet = workbook.getSheetAt(0);
        int lastRowNum = sheet.getLastRowNum();
        Row newRow = sheet.createRow(lastRowNum + 1);

        newRow.createCell(0).setCellValue(activity.getUserId());
        newRow.createCell(1).setCellValue(activity.getWebId());

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
    }

    private void removeLikeFromExcel(LikeActivity activity) throws IOException {
        String filename = "likeActivity.xlsx";
        File file = new File(filename);
        if (file.exists()) {
            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row.getCell(0).getStringCellValue().equals(activity.getUserId()) &&
                        row.getCell(1).getStringCellValue().equals(activity.getWebId())) {
                    sheet.removeRow(row);
                    break;
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    @PostMapping("/saveLike")
    public ResponseEntity<?> saveLike(@RequestBody LikeActivity activity) {
        try {
            // Save like to Excel
            saveLikeToExcel(activity);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getUserLikes")
    public ResponseEntity<Object> getUserLikes(@RequestParam String userId) { // Changed return type to Object for flexibility
        List<String> likedWebIds = new ArrayList<>();

        try {
            likedWebIds = getLikedWebIdsFromExcel(userId);

            // Log the fetched likedWebIds
            //System.out.println("Fetched likedWebIds for userId " + userId + ": " + likedWebIds);

            if (likedWebIds.isEmpty()) {
                return new ResponseEntity<>("No liked web IDs found for user " + userId, HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity<>(likedWebIds, HttpStatus.OK);
        } catch (Exception e) {
            // Log the error
            System.err.println("Error fetching likedWebIds for userId " + userId + ": " + e.getMessage());

            // Returning the exception's message for debugging (be cautious with this in production)
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> getLikedWebIdsFromExcel(String userId) throws Exception {
        List<String> likedWebIds = new ArrayList<>();

        // Path to your Excel file
        String excelFilePath = "likeActivity.xlsx";
        FileInputStream fis = new FileInputStream(new File(excelFilePath));

        // Create a workbook instance for .xlsx
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);

        // Iterate through each rows one by one
        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            // Skip the header
            if (row.getRowNum() == 0) {
                continue;
            }

            // Assuming first cell in the row contains userId and the second cell contains webId
            Cell userIdCell = row.getCell(0);
            Cell webIdCell = row.getCell(1);

            // Check if the current row's userId matches the given userId
            if (userIdCell != null && userIdCell.getCellType() == CellType.STRING && userIdCell.getStringCellValue().equals(userId)) {
                if (webIdCell != null && webIdCell.getCellType() == CellType.STRING) {
                    likedWebIds.add(webIdCell.getStringCellValue());
                }
            }
        }

        workbook.close();
        fis.close();

        return likedWebIds;
    }

    // Save Star (or favorite) to Excel
    private void saveStarToExcel(StarActivity activity) throws Exception {
        String filename = "starActivity.xlsx";
        File file = new File(filename);
        Workbook workbook;

        if (!file.exists()) {
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("StarActivity");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("User ID");
            header.createCell(1).setCellValue("Web ID");
            header.createCell(2).setCellValue("Web Abstract");  // Add this for webab
        } else {
            workbook = new XSSFWorkbook(new FileInputStream(file));
        }

        Sheet sheet = workbook.getSheetAt(0);
        int lastRowNum = sheet.getLastRowNum();
        Row newRow = sheet.createRow(lastRowNum + 1);

        String ab = summary.getSummary(activity.getWebab());
        ab = ab.replaceAll("[BREAK]","");
        newRow.createCell(0).setCellValue(activity.getUserId());
        newRow.createCell(1).setCellValue(activity.getWebId());
        newRow.createCell(2).setCellValue(ab);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
    }

    // Remove Star (or favorite) from Excel
    private void removeStarFromExcel(LikeActivity activity) throws IOException {
        String filename = "starActivity.xlsx";
        File file = new File(filename);
        if (file.exists()) {
            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row.getCell(0).getStringCellValue().equals(activity.getUserId()) &&
                        row.getCell(1).getStringCellValue().equals(activity.getWebId())) {
                    sheet.removeRow(row);
                    break;
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    // Endpoint to Save Star
    @PostMapping("/saveStar")
    public ResponseEntity<?> saveStar(@RequestBody StarActivity activity) {
        try {
            saveStarToExcel(activity);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get User's Starred Items
    @GetMapping("/getUserStars")
    public ResponseEntity<Object> getUserStars(@RequestParam String userId) {
        List<String> starredWebIds = new ArrayList<>();

        try {
            starredWebIds = getStarredWebIdsFromExcel(userId);

            if (starredWebIds.isEmpty()) {
                return new ResponseEntity<>("No starred web IDs found for user " + userId, HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity<>(starredWebIds, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Fetch starred Web IDs from Excel for a specific user
    private List<String> getStarredWebIdsFromExcel(String userId) throws Exception {
        List<String> starredWebIds = new ArrayList<>();

        String excelFilePath = "starActivity.xlsx";
        FileInputStream fis = new FileInputStream(new File(excelFilePath));

        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);

        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            if (row.getRowNum() == 0) {
                continue;
            }

            Cell userIdCell = row.getCell(0);
            Cell webIdCell = row.getCell(1);

            if (userIdCell != null && userIdCell.getCellType() == CellType.STRING && userIdCell.getStringCellValue().equals(userId)) {
                if (webIdCell != null && webIdCell.getCellType() == CellType.STRING) {
                    starredWebIds.add(webIdCell.getStringCellValue());
                }
            }
        }

        workbook.close();
        fis.close();

        return starredWebIds;
    }


}

@Data
class ClickActivity {
    private String userId;
    private String webId;
    private String timestamp;  // 新增的时间戳字段

    // This will get the current timestamp in desired format when an object is created.
    public ClickActivity() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        this.timestamp = sdf.format(new Date());
    }
}

@Data
class LikeActivity {
    private String userId;
    private String webId;
}




