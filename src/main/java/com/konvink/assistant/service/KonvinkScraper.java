package com.konvink.assistant.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Laedt Praxisauftrags-PDFs vom Konvink-Portal herunter.
 *
 * Java-Pendant zu konvink_scraper.py (dort via playwright-python).
 *
 * Login- und erste Navigationsschritte basieren auf einer echten
 * Playwright-Codegen-Aufzeichnung (Login unter https://app.konvink.ch,
 * danach Klick auf "Mein Portfolio" und "Kompetenzentwicklung im
 * Ueberblick"). Der letzte Navigationsschritt zu "Bearbeiten -> ueber
 * alle Handlungskompetenzen" sowie der eigentliche PDF-Download sind noch
 * nicht final verifiziert (die Aufzeichnung endete vor der PDF-Liste) -
 * ggf. muss hier nach einem Testlauf noch nachjustiert werden.
 */
public final class KonvinkScraper {

    private static final Logger LOGGER = Logger.getLogger(KonvinkScraper.class.getName());

    private KonvinkScraper() {
    }

    public static void downloadPraxisauftraegePdfs(
            String loginUrl, String username, String password, Path downloadFolder) {

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setAcceptDownloads(true));
            Page page = context.newPage();

            // Schritt 1: Login (echte Selektoren aus Codegen-Aufzeichnung).
            page.navigate(loginUrl);
            page.getByTitle("Login").click();
            page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName("Gib deine E-Mail-Adresse ein.").setExact(true))
                    .fill(username);
            page.getByLabel("Gib dein Passwort ein.").fill(password);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Anmelden")).click();

            // Schritt 2: Navigation zu Mein Portfolio -> Kompetenzentwicklung.
            page.locator("#my-portfolio-button").click();
            page.locator("#dashboard-competencecompass-navigate").click();

            // Schritt 3: "Bearbeiten" -> "ueber alle Handlungskompetenzen".
            // TODO: In der Aufzeichnung war dies ein Link ohne sichtbaren
            // Namen (Icon-Button) - ggf. praeziseren, sobald ein Testlauf
            // zeigt, ob dieser Klick zuverlaessig den richtigen Link trifft.
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Bearbeiten")).click();

            // TODO: Sobald die Liste der Handlungskompetenzen/Praxisauftraege
            // sichtbar ist, muessen hier die echten Selektoren fuer
            // "ueber alle Handlungskompetenzen" sowie fuer die einzelnen
            // PDF-Download-Links ermittelt werden. Platzhalter-Beispiel:
            try {
                page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("über alle Handlungskompetenzen")).click();
            } catch (Exception exc) {
                LOGGER.log(Level.WARNING,
                        "Link 'ueber alle Handlungskompetenzen' nicht gefunden - "
                                + "Selektor muss anhand einer Live-Session verifiziert werden.", exc);
            }

            // TODO: PDF-Downloads - Beispielmuster, sobald die Download-Links
            // bekannt sind:
            // for (Locator link : page.locator("a[href$='.pdf']").all()) {
            //     Download download = page.waitForDownload(link::click);
            //     download.saveAs(downloadFolder.resolve(download.suggestedFilename()));
            // }

            LOGGER.warning(
                    "KonvinkScraper.downloadPraxisauftraegePdfs: Login und erste "
                            + "Navigation sind jetzt mit echten Selektoren hinterlegt. Die "
                            + "finale Navigation zur PDF-Liste sowie der Download selbst "
                            + "sind noch zu verifizieren/implementieren.");

            browser.close();
        }
    }
}
