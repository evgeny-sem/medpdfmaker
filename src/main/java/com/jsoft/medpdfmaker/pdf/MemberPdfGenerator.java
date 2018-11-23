package com.jsoft.medpdfmaker.pdf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.jsoft.medpdfmaker.Constants;
import com.jsoft.medpdfmaker.AppProperties;
import com.jsoft.medpdfmaker.domain.ServiceRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import static com.jsoft.medpdfmaker.util.FileUtil.stripLastSlashIfNeeded;

public class MemberPdfGenerator {

    private static final int ROWS_COUNT = 6;

    private AppProperties appProperties;

    private final DateTimeFormatter formatYearCentury = DateTimeFormatter.ofPattern("yy");
    private final DateTimeFormatter formatDay = DateTimeFormatter.ofPattern("dd");
    private final DateTimeFormatter formatMonth = DateTimeFormatter.ofPattern("MM");
    private final DecimalFormat formatMoney = new DecimalFormat("0.00");

    public MemberPdfGenerator(final AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<Path> generate(final Path workFolder, final List<ServiceRecord> memberServiceRecords) throws IOException {
        if (CollectionUtils.isEmpty(memberServiceRecords)) {
            return Collections.emptyList();
        }
        final List<Path> result = new LinkedList<>();
        final ServiceRecord headerRecord = memberServiceRecords.get(0);
        List<ServiceRecord> pageRecords = new LinkedList<>();
        final PageInfo pageInfo = new PageInfo(memberServiceRecords.size(), ROWS_COUNT);
        for (final ServiceRecord memberServiceRecord : memberServiceRecords) {
            pageRecords.add(memberServiceRecord);
            if (pageRecords.size() == ROWS_COUNT) {
                result.add(generatePage(pageInfo, headerRecord, pageRecords, workFolder));
                pageRecords = new LinkedList<>();
                pageInfo.incPageNum();
            }
        }
        if (!pageRecords.isEmpty()) {
            result.add(generatePage(pageInfo, headerRecord, pageRecords, workFolder));
        }
        return result;
    }

    private Path generatePage(PageInfo pageInfo, ServiceRecord headerRecord,
                              List<ServiceRecord> pageRecords, Path workFolder) throws IOException {
        final String pageFileName = makePageFileName(headerRecord, pageInfo.pageNum, workFolder);
        PDDocument pdDocument = null;
        try (InputStream templateStream = getTemplateStream()) {
            pdDocument = PDDocument.load(templateStream);
            fillPageHeader(pdDocument, headerRecord, pageInfo);
            fillPageTable(pdDocument, pageRecords, pageInfo);
            fillPageFooter(pdDocument, pageInfo);
            pdDocument.save(pageFileName);
        } finally {
            if (pdDocument != null) {
                pdDocument.close();
            }
        }
        return Paths.get(pageFileName);
    }

    private void fillPageHeader(PDDocument pdDocument, ServiceRecord headerRecord, PageInfo pageInfo) throws IOException {
        String memberIdPage = headerRecord.getMemberId() + (pageInfo.multiPaged ? pageInfo.pageNumWithPrefix() : "");
        setField(pdDocument, "Text1", memberIdPage);
        setField(pdDocument, "Text2", headerRecord.getFAndLName());
        String origin = headerRecord.getOrigin();
        int originSlashPos = origin.indexOf("/");
        if (originSlashPos > -1) {
            setField(pdDocument,"Text3", origin.substring(0, originSlashPos));
        } else {
            setField(pdDocument,"Text3", origin);
        }
        if (StringUtils.isNotEmpty(headerRecord.getCity())) {
            setField(pdDocument,"Text4", headerRecord.getCity());
        }
        if (StringUtils.isNotEmpty(headerRecord.getState())) {
            setField(pdDocument,"Text5", headerRecord.getState());
        }
        if (StringUtils.isNotEmpty(headerRecord.getZipCode())) {
            setField(pdDocument,"Text6", headerRecord.getZipCode());
        }
        if (StringUtils.isNotEmpty(headerRecord.getAreaCode())) {
            setField(pdDocument,"Text7", headerRecord.getAreaCode());
        }
        if (StringUtils.isNotEmpty(headerRecord.getPhone())) {
            setField(pdDocument,"Text8", headerRecord.getPhone());
        }
        LocalDate dob = headerRecord.getDob();
        setField(pdDocument,"Text9", formatMonth.format(dob));
        setField(pdDocument,"Text10", formatDay.format(dob));
        setField(pdDocument,"Text11", formatYearCentury.format(dob));
        setField(pdDocument,"Text54", appProperties.getFederalTaxID());
        setField(pdDocument,"Text57", appProperties.getProvider());
    }

    private void fillPageTable(PDDocument pdDocument, List<ServiceRecord> pageRecords, PageInfo pageInfo) throws IOException {
        final double charges = appProperties.getCharges();
        int recNum = 0;
        for (final ServiceRecord pageRecord : pageRecords) {
            int fieldIdxShift = recNum * 7;
            setField(pdDocument,"Text" + (15 + fieldIdxShift), appProperties.getPlaceOfService());
            setField(pdDocument,"Text" + (16 + fieldIdxShift), appProperties.getProcedures());
            setField(pdDocument,"Text" + (17 + fieldIdxShift),formatMoney.format(charges));
            setField(pdDocument,"Text" + (18 + fieldIdxShift), pageRecord.getRefId());
            LocalDate pickUpDate = pageRecord.getPickupDate();
            setField(pdDocument,"Text" + (12 + fieldIdxShift), formatMonth.format(pickUpDate));
            setField(pdDocument,"Text" + (13 + fieldIdxShift), formatDay.format(pickUpDate));
            setField(pdDocument,"Text" + (14 + fieldIdxShift), formatYearCentury.format(pickUpDate));
            recNum++;
        }
    }

    private void fillPageFooter(PDDocument pdDocument, PageInfo pageInfo) throws IOException {
        final double charges = appProperties.getCharges();
        if (pageInfo.lastPage()) {
            setField(pdDocument,"Text56", formatMoney.format(charges * pageInfo.recordsCount));
        } else {
            setField(pdDocument,"Text56", "See page " + pageInfo.pageCount);
        }
    }

    private String makePageFileName(ServiceRecord headerRecord, int pageNum, Path workFolder) {
        final String normalizedMemberId = headerRecord.getMemberId().replaceAll("[^a-zA-Z0-9.-]", "_");
        return stripLastSlashIfNeeded(workFolder.toFile().getAbsolutePath()) +
               File.separator +
               String.format("%s_%03d.pdf", normalizedMemberId, pageNum);
    }

    private InputStream getTemplateStream() {
        final InputStream result = this.getClass().getClassLoader().getResourceAsStream(Constants.PDF_TEMPLATE_RESOURCE_PATH);
        if (result == null) {
            throw new IllegalStateException(String.format("Template resource %s is not found", Constants.PDF_TEMPLATE_RESOURCE_PATH));
        } else {
            return result;
        }
    }

    public static void setField(final PDDocument pdDocument, final String fName, final String fValue) throws IOException {
        final PDDocumentCatalog pdDocumentCatalog = pdDocument.getDocumentCatalog();
        final PDAcroForm pdAcroForm = pdDocumentCatalog.getAcroForm();
        final PDField field = pdAcroForm.getField(fName);
        if (field == null) {
            throw new IllegalArgumentException(String.format("No field %s found in PDF documeent", fName));
        } else {
            field.setValue(fValue);
        }
    }

    private static class PageInfo {

        int pageNum;
        final int recordsCount;
        final int pageCount;
        final boolean multiPaged;

        // memberServiceRecords
        PageInfo(int recordsCount, int recordsOnPage) {
            this.recordsCount = recordsCount;
            this.pageNum = 1;
            this.pageCount = (int)Math.round(Math.ceil((double)recordsCount / recordsOnPage));
            this.multiPaged = this.pageCount > 1;

        }

        void incPageNum() {
            pageNum++;
        }

        boolean lastPage() {
            return pageNum == pageCount;
        }

        String pageNumWithPrefix() {
            return "_" + pageNum;
        }
    }
}