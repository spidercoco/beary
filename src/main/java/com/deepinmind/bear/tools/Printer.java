package com.deepinmind.bear.tools;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.deepinmind.bear.oss.OSSService;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import javax.print.attribute.standard.Chromaticity;
import java.io.*;

/**
 * Utility class for printing documents using the default system printer
 */
@Component
@Slf4j
public class Printer {


    @Autowired
    OSSService ossService;

    /**
     * Prints a text file using the default system printer
     * @param filePath Path to the file to be printed
     * @param isColor true for color printing, false for black and white
     * @throws PrintException if printing fails
     * @throws IOException if file reading fails
     */
    @Tool(name = "print_file", description = "打印文件")
    public void printFile(@ToolParam(name = "filePath", description = "文件地址") String filePath, @ToolParam(name = "isColor", description = "是否彩色打印") boolean isColor) throws PrintException, IOException {
        log.info("Printing file: {}", filePath);
        File file = new File(filePath);
        if (!file.exists()) {
            ossService.downloadFile("oss-filelist", filePath, filePath);
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + filePath);
            }
        }

        // Get the default printer
        javax.print.PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultService == null) {
            throw new PrintException("No default printer found");
        }

        System.out.println("Using printer: " + defaultService.getName());
        System.out.println("Print mode: " + (isColor ? "Color" : "Black & White"));

        // Create a DocFlavor for text/plain files
        DocFlavor flavor = filePath.endsWith(".pdf") ? DocFlavor.INPUT_STREAM.PDF : DocFlavor.INPUT_STREAM.AUTOSENSE;

        // Create print attributes
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(1)); // Print one copy
        attributes.add(isColor ? Chromaticity.COLOR : Chromaticity.MONOCHROME); // Color or black/white

        // Create the document to print
        Doc doc = new SimpleDoc(new FileInputStream(file), flavor, null);

        // Create a print job
        DocPrintJob printJob = defaultService.createPrintJob();

        // Submit the print job
        printJob.print(doc, attributes);

        System.out.println("Print job submitted successfully.");
    }
    
    /**
     * Prints a text file using the default system printer (black & white by default)
     * @param filePath Path to the file to be printed
     * @throws PrintException if printing fails
     * @throws IOException if file reading fails
     */
    public void printFile(String filePath) throws PrintException, IOException {
        printFile(filePath, false); // Default to black & white
    }

    /**
     * Prints text content directly using the default system printer
     * @param textContent The text content to print
     * @param isColor true for color printing, false for black and white
     * @throws PrintException if printing fails
     */
    public static void printText(String textContent, boolean isColor) throws PrintException {
        // Get the default printer
        javax.print.PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultService == null) {
            throw new PrintException("No default printer found");
        }

        System.out.println("Using printer: " + defaultService.getName());
        System.out.println("Print mode: " + (isColor ? "Color" : "Black & White"));

        // Create a DocFlavor for text/plain
        DocFlavor flavor = DocFlavor.CHAR_ARRAY.TEXT_PLAIN;

        // Create print attributes
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(1)); // Print one copy
        attributes.add(isColor ? Chromaticity.COLOR : Chromaticity.MONOCHROME); // Color or black/white

        // Create the document to print
        Doc doc = new SimpleDoc(textContent.toCharArray(), flavor, null);

        // Create a print job
        DocPrintJob printJob = defaultService.createPrintJob();

        // Submit the print job
        printJob.print(doc, attributes);

        System.out.println("Print job submitted successfully.");
    }
    
    /**
     * Prints text content directly using the default system printer (black & white by default)
     * @param textContent The text content to print
     * @throws PrintException if printing fails
     */
    public static void printText(String textContent) throws PrintException {
        printText(textContent, false); // Default to black & white
    }


    public static void main(String[] args) {
        // Print default printer information
        javax.print.PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultService != null) {
            System.out.println("Default printer: " + defaultService.getName());
        } else {
            System.out.println("No default printer found");
        }
        
        // List all available printers
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        System.out.println("Available printers (" + services.length + "):");
        for (int i = 0; i < services.length; i++) {
            System.out.println((i + 1) + ". " + services[i].getName());
        }
        
        if (args.length == 0) {
            System.out.println("\nUsage: java PrinterUtil <file_path> [color|bw]");
            System.out.println("Options:");
            System.out.println("  color - Print in color (default for color-capable printers)");
            System.out.println("  bw    - Print in black & white");
            System.out.println("\nExamples:");
            System.out.println("  java PrinterUtil document.pdf");
            System.out.println("  java PrinterUtil document.pdf color");
            System.out.println("  java PrinterUtil document.pdf bw");
            return;
        }

        try {
            String filePath = args[0];
            boolean isColor = false; // Default to black & white
            
            // Check if color option is specified
            if (args.length > 1) {
                String colorOption = args[1].toLowerCase();
                if (colorOption.equals("color")) {
                    isColor = true;
                } else if (colorOption.equals("bw")) {
                    isColor = false;
                } else {
                    System.out.println("Warning: Unknown color option '" + args[1] + "'. Using black & white.");
                }
            }
            
            System.out.println("\nPrinting file: " + filePath);
            System.out.println("Mode: " + (isColor ? "Color" : "Black & White"));
            
            if (filePath.toLowerCase().endsWith(".pdf")) {            } else {
                // printFile(filePath, isColor);
            }
        } catch (Exception e) {
            System.err.println("Error printing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}