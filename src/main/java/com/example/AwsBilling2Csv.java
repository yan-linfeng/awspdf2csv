package com.example;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * This is a tool that convert aws billing pdf to csv file
 *
 * @author LinFeng Yan
 */
public class AwsBilling2Csv extends PDFTextStripper {
    private static LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, List<String[]>>>>> parsedBillingTree = new LinkedHashMap<>();
    private static String status = "";
    private static String lv1 = "";
    private static String lv2 = "";
    private static String lv3 = "";
    private static String lv4 = "";
    private static float y = 0f;
    private static boolean isLv4 = false;

    public AwsBilling2Csv() throws IOException {}

    public static void main(String[] args) throws IOException {
        if(args.length < 1){
            System.err.println("No file path specified");
            System.exit(1);
        }
        String filePath = args[0];
        // String filePath = "/Users/yanlinfeng/Documents/pdf/Billing Management Console.pdf";
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new AwsBilling2Csv();
            stripper.setSortByPosition(true);
            stripper.setStartPage(0);
            stripper.setEndPage(document.getNumberOfPages());

            Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
            stripper.writeText(document, dummy);
        }
        outputBilling2Csv();
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        TextPosition firstCharPosition = textPositions.get(0);
        float x = firstCharPosition.getX();
        float fontSizePt = firstCharPosition.getFontSizeInPt();
        float currentY = firstCharPosition.getEndY();
        float fontSize = firstCharPosition.getFontSize();

        if ("".equals(status) && "サービス別料⾦".equals(string)) {
            status = "started";
            System.out.println("Parsing AWS Billing PDF Started");
        } else if ("started".equals(status)) {
            if (string.startsWith("合計税額") && fontSize == 14.0f) {
                status = "end";
                System.out.println("Parsing AWS Billing PDF End");
                return;
            }

            Set<String> headers = new HashSet<>(3);
            headers.add("説明");
            headers.add("使⽤量");
            headers.add("⾦額 USD");
            // if (headers.contains(string) && fontSize > 10.6f && fontSize < 10.7f && fontSizePt >= 6.9f && fontSizePt <= 7.1f) {
            if (headers.contains(string)) {
                return;
            }
            
            // possible font size: 14.66 12.0 10.66 10.0
            // possible x values: 0.0 6.0025215 10.504413 21.008825
            // System.out.println(x);
            // System.out.println(fontSize);
            System.out.println(x);
            if (x == 0f && fontSize == 14.66f) {
                // Lv1
                lv1 = string;
                parsedBillingTree.put(lv1, new LinkedHashMap<>());
            } else if (x >= 6f && x <= 6.1f && fontSize == 12.0f) {
                // Lv2
                lv2 = string;
                parsedBillingTree.get(lv1).put(lv2, new LinkedHashMap<>());
            } else if (x >= 6f && x <= 6.1f && fontSize == 10.66f) {
                // lv3
                lv3 = string;
                parsedBillingTree.get(lv1).get(lv2).put(lv3, new LinkedHashMap<>());
            } else if (x >= 10.2f && x <= 10.6f && fontSize == 10.0f) {
                // lv4
                lv4 = string;
                parsedBillingTree.get(lv1).get(lv2).get(lv3).put(lv4, new ArrayList<String[]>());
            } else if (x >= 20f && fontSize == 10.0f) {
                // lv5
                List<String[]> lv5List = parsedBillingTree.get(lv1).get(lv2).get(lv3).get(lv4);

                if (currentY != y) {
                    if(lv5List == null){
                        lv4 = "placeholder";
                        parsedBillingTree.get(lv1).get(lv2).get(lv3).put(lv4, new ArrayList<String[]>());
                        return;
                        // System.out.println(lv5List);
                    }
                    // merge two lines into one record when one record span over 2 lines
                    mergeLines2OneRecord(lv5List);

                    String[] record = new String[3];
                    if (x >= 20f && x<= 22f) {
                        record[0] = string;
                    } else if (x >= 380f && x <= 386f) {
                        record[1] = string;
                    } else if (x >= 450f) {
                        record[2] = string;
                    }
                    lv5List.add(record);
                    y = currentY;
                } else {
                    if (lv5List.size() == 0) {
                        System.out.println(lv5List);
                    }
                    String[] record = lv5List.get(lv5List.size() - 1);
                    if (x >= 380f && x <= 386f) {
                        record[1] = string;
                    } else if (x >= 450f) {
                        record[2] = string;
                    }
                }
            }
        } else {
            return;
        }
    }

    private static void outputBilling2Csv() {
        try (FileWriter fw = new FileWriter("billing.csv", Charsets.UTF_8, false)) {
            for (Entry<String, LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, List<String[]>>>>> lv1Entry : parsedBillingTree
                    .entrySet()) {
                String svp = lv1Entry.getKey();
                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, List<String[]>>>> lv1Value = lv1Entry
                        .getValue();
                for (Entry<String, LinkedHashMap<String, LinkedHashMap<String, List<String[]>>>> lv2Entry : lv1Value
                        .entrySet()) {
                    String serviceCategory = lv2Entry.getKey();
                    LinkedHashMap<String, LinkedHashMap<String, List<String[]>>> lv2Value = lv2Entry.getValue();
                    for (Entry<String, LinkedHashMap<String, List<String[]>>> lv3Entry : lv2Value.entrySet()) {
                        String region = lv3Entry.getKey();
                        LinkedHashMap<String, List<String[]>> lv3Value = lv3Entry.getValue();
                        for (Entry<String, List<String[]>> lv4Entry : lv3Value.entrySet()) {
                            String serviceName = lv4Entry.getKey();
                            List<String[]> lv4Value = lv4Entry.getValue();
                            for (String[] record : lv4Value) {
                                if (record[0] == null || record[1] == null) {
                                    continue;
                                }
                                String shape = "";
                                if (record[0].matches(".*?\s{0,1}?([a-z0-9\\.]+(micro|small|medium|large)).*")) {
                                    shape = record[0]
                                            .replaceAll(".*?\s{0,1}?([a-z0-9\\.]+(micro|small|medium|large)).*", "$1");
                                }
                                String usageQuantiry = "";
                                if (record[1].matches("[0-9,\\.]+[0-9]* .*")) {
                                    usageQuantiry = record[1].replaceAll("([0-9,\\.]+[0-9]*) .*", "$1").replaceAll(",",
                                            "");
                                }
                                String csvRecord = String.format(
                                        "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"", svp,
                                        serviceCategory,
                                        region, serviceName, record[0], record[1], record[2], shape, usageQuantiry);
                                fw.write(csvRecord + "\r\n");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void mergeLines2OneRecord(List<String[]> records) {
        if (records == null ||records.size() < 2) {
            return;
        }

        String[] lastRecord = records.get(records.size() - 1);
        if (lastRecord[0] == null || lastRecord[1] == null || lastRecord[2] == null) {
            String[] secondLastRecord = records.get(records.size() - 2);
            if (lastRecord[0] != null) {
                secondLastRecord[0] += lastRecord[0];
            }
            if (lastRecord[1] != null) {
                secondLastRecord[1] += lastRecord[1];
            }
            if (lastRecord[2] != null) {
                secondLastRecord[2] += lastRecord[2];
            }
            records.remove(records.size() - 1);
        }
    }

}
