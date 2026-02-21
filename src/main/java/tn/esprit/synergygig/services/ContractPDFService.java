package tn.esprit.synergygig.services;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.*;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.itextpdf.io.image.ImageDataFactory;

import tn.esprit.synergygig.entities.Contract;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.nio.file.Path;


import java.io.File;
import java.io.InputStream;

public class ContractPDFService {

    public String generatePDF(Contract contract) {

        try {

            File folder = new File("contracts");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder,
                    "contract_" + contract.getId() + ".pdf");

            String absolutePath = file.getAbsolutePath();

            PdfWriter writer = new PdfWriter(absolutePath);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            // ===== HEADER =====
            Paragraph header = new Paragraph("SYNERGYGIG LEGAL CONTRACT")
                    .setFontSize(20)
                    .setBold()
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(new DeviceRgb(15, 25, 80))
                    .setPadding(12);

            document.add(header);
            document.add(new Paragraph("\n"));

            // ===== LOGO =====
            InputStream logoStream = getClass()
                    .getResourceAsStream("/tn/esprit/synergygig/gui/images/anaslogo1.png");

            if (logoStream != null) {
                Image logo = new Image(
                        ImageDataFactory.create(logoStream.readAllBytes()))
                        .scaleToFit(130, 130)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);

                document.add(logo);
            }

            document.add(new Paragraph("\n"));

            // ===== CONTRACT BOX =====
            Div contractBox = new Div()
                    .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 1))
                    .setPadding(20);

            contractBox.add(new Paragraph("Contract ID: " + contract.getId()).setBold());
            contractBox.add(new Paragraph("Start Date: " + contract.getStartDate()));
            contractBox.add(new Paragraph("End Date: " + contract.getEndDate()));
            contractBox.add(new Paragraph("Terms:"));
            contractBox.add(new Paragraph(contract.getTerms()));
            contractBox.add(new Paragraph("Risk Score: " + contract.getRiskScore())
                    .setFontColor(ColorConstants.RED));

            document.add(contractBox);

            document.add(new Paragraph("\n\n"));

            // ===== SIGNATURE AREA =====
            Div signArea = new Div().setTextAlignment(TextAlignment.CENTER);

            InputStream signatureStream = getClass()
                    .getResourceAsStream("/tn/esprit/synergygig/gui/images/signature.png");

            if (signatureStream != null) {
                Image signature = new Image(
                        ImageDataFactory.create(signatureStream.readAllBytes()))
                        .scaleToFit(150, 80);
                signArea.add(signature);
            }

            signArea.add(new Paragraph("Anas Chagour")
                    .setBold()
                    .setFontSize(14));

            signArea.add(new Paragraph("Founder - SynergyGig"));

            InputStream stampStream = getClass()
                    .getResourceAsStream("/tn/esprit/synergygig/gui/images/cachet.png");

            if (stampStream != null) {
                Image stamp = new Image(
                        ImageDataFactory.create(stampStream.readAllBytes()))
                        .scaleToFit(120, 120);
                signArea.add(stamp);
            }

            document.add(signArea);

            document.add(new Paragraph("\n\n"));

            document.add(new Paragraph("Digitally generated and legally binding document.")
                    .setFontSize(9)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
            // ===== QR BLOCKCHAIN =====
            try {

                String qrContent =
                        contract.getId() + "|" + contract.getBlockchainHash();

                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                BitMatrix bitMatrix =
                        qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 200, 200);

                File qrFile = new File("contracts/qr_" + contract.getId() + ".png");

                MatrixToImageWriter.writeToPath(
                        bitMatrix,
                        "PNG",
                        qrFile.toPath()
                );

                Image qrImage = new Image(
                        ImageDataFactory.create(qrFile.getAbsolutePath()))
                        .scaleToFit(150, 150)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);

                document.add(new Paragraph("\n\nBlockchain Verification QR")
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER));

                document.add(qrImage);

            } catch (Exception e) {
                e.printStackTrace();
            }

            document.close();

            System.out.println("🔥 Premium Legal PDF Generated");

            return absolutePath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
