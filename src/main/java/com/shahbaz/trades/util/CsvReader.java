package com.shahbaz.trades.util;

import com.shahbaz.trades.model.entity.Margin;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {

    public static List<Margin> read(MultipartFile file, float leverage) {
        try (CSVParser parser = CSVParser.parse(
                file.getInputStream(),
                StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .get()    // ✅ NOT deprecated
        )) {

            List<Margin> list = new ArrayList<>();
            for (CSVRecord record : parser) {
                String symbol = record.get("tradingsymbol");
                float lev = Float.parseFloat(record.get("leverage"));

                if (lev >= leverage) {
                    list.add(Margin.builder()
                            .symbol(symbol)
                            .name(symbol)
                            .margin(lev)
                            .build());

                }
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("CSV parsing failed", e);
        }
    }

}
