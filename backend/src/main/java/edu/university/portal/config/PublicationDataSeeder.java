package edu.university.portal.config;

import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.repository.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Profile("dev & !test")
@Order(0)
public class PublicationDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicationDataSeeder.class);

    private static final String[] ROWS = {
            "Efficient Index Structures for Research Metadata|Anika Kapoor; Luis Chen|10.5555/uni.cs.2024.001|2024|Computer Science|IEEE Transactions on Knowledge and Data Engineering|0000-0001-5100-0001|CROSSREF|VERIFIED",
            "Graph Models of Citation Networks|Meera Iyer|10.5555/uni.cs.2023.014|2023|Computer Science|Journal of the Association for Information Science and Technology||FORM|VERIFIED",
            "Privacy-Preserving Record Linkage for Institutional Repositories|Samir Rahman; Paul Okonkwo|10.5555/uni.cs.2022.008|2022|Computer Science|The VLDB Journal||CROSSREF|VERIFIED",
            "Lightweight Checks for Publication Metadata Quality|Noah Alvarez|10.5555/uni.cs.2026.002|2026|Computer Science|Proceedings of the Joint Conference on Digital Libraries|0000-0001-5100-0004|FORM|VERIFIED",
            "A Survey of Persistent Identifiers in Scholarly Communication|Helena Bergstrom|10.5555/uni.cs.2021.019|2021|Computer Science|Scientometrics||ORCID|VERIFIED",
            "Blockchain for University Archives|Jay Patel|10.5555/uni.cs.2020.011|2020|Computer Science|Unreviewed Preprint Series||FORM|REJECTED",
            "Seasonal Variation in Campus Wetland Microbiomes|Rhea Singh; Elena Novak|10.5555/uni.bio.2024.006|2024|Biology|Environmental Microbiology||CROSSREF|VERIFIED",
            "Drought Response in Experimental Wheat Lines|Carlos Duarte; Maryam Hassan|10.5555/uni.bio.2019.003|2019|Biology|Plant Physiology||FORM|VERIFIED",
            "Annotating Local Herbarium Specimens with Genomic Barcodes|Yuki Kobayashi|10.5555/uni.bio.2025.001|2025|Biology|Applications in Plant Sciences|0000-0001-5100-0009|ORCID|VERIFIED",
            "Pollinator Diversity across the North Quadrangle|Leah Moreau; Tariq Ibrahim|10.5555/uni.bio.2023.012|2023|Biology|Journal of Insect Conservation||FORM|IN_REVIEW",
            "Antibiotic Stewardship in the Teaching Hospital|Francesca Rossi|10.5555/uni.bio.2022.018|2022|Biology|Clinical Infectious Diseases||CROSSREF|VERIFIED",
            "Field Notes on the Riverside Bird Count|Anika Kapoor; Helen Cho|10.5555/uni.bio.2026.004|2026|Biology|The Condor||FORM|VERIFIED",
            "Low-Cost Interferometry for Teaching Laboratories|Dmitri Petrov; Sarah Lang|10.5555/uni.phy.2021.004|2021|Physics|American Journal of Physics||FORM|VERIFIED",
            "Thermal Noise in Tabletop Gravitational Sensors|Ingrid Novak|10.5555/uni.phy.2024.009|2024|Physics|Review of Scientific Instruments||CROSSREF|VERIFIED",
            "Spectroscopy of Dye-Sensitized Campus Solar Cells|Priya Ghosh; Ruth Ellis|10.5555/uni.phy.2019.015|2019|Physics|Journal of Chemical Education||FORM|VERIFIED",
            "Unsupported Claims in a Cold-Fusion Replication|Blake Quinn|10.5555/uni.phy.2025.020|2025|Physics|Unreviewed Preprint Series||FORM|REJECTED",
            "Muon Flux Measurements at Mid-Latitude|Hiro Abe; Camila Silva|10.5555/uni.phy.2023.005|2023|Physics|Physical Review Physics Education Research||ORCID|VERIFIED",
            "Calibration Notes for the Rooftop Weather Mast|Marta Lindholm|10.5555/uni.phy.2026.001|2026|Physics|Journal of Atmospheric and Oceanic Technology|0000-0001-5100-0018|FORM|VERIFIED",
            "Tuition Aid and Graduate Retention in Regional Universities|Adaeze Okeke; John Martin|10.5555/uni.econ.2022.010|2022|Economics|Journal of Higher Education Policy||CROSSREF|VERIFIED",
            "Local Labour Markets around University Towns|Stefan Berger|10.5555/uni.econ.2020.006|2020|Economics|Regional Studies||FORM|VERIFIED",
            "Open-Access Fees and Departmental Publishing Patterns|Nadia Farouk; Lukas Steiner|10.5555/uni.econ.2024.013|2024|Economics|Journal of the Association for Information Science and Technology||FORM|VERIFIED",
            "A Primer on Research Output Metrics|Claire Walsh|10.5555/uni.econ.2019.002|2019|Economics|Research Evaluation||FORM|IN_REVIEW",
            "Grant Cycles and Publication Lags|Rafael Almeida|10.5555/uni.econ.2025.008|2025|Economics|Research Policy|0000-0001-5100-0023|CROSSREF|VERIFIED",
            "Library Expenditure and Citation Impact|Mei Chen; Anika Kapoor|10.5555/uni.econ.2021.016|2021|Economics|College and Research Libraries||ORCID|VERIFIED",
            "Additive Manufacturing of Laboratory Fixtures|Krzysztof Nowak; Priya Singh|10.5555/uni.me.2023.007|2023|Mechanical Engineering|Journal of Manufacturing Processes||CROSSREF|VERIFIED",
            "Heat Recovery in Campus District Heating|Lena Fischer|10.5555/uni.me.2019.004|2019|Mechanical Engineering|Energy and Buildings||FORM|VERIFIED",
            "Bicycle Frame Fatigue under Commuter Loads|Thanh Nguyen; Rosa Costa|10.5555/uni.me.2024.011|2024|Mechanical Engineering|International Journal of Fatigue||FORM|VERIFIED",
            "Perpetual Motion Workshop Device|Elliot Brown|10.5555/uni.me.2022.019|2022|Mechanical Engineering|Unreviewed Preprint Series||FORM|REJECTED",
            "Quiet HVAC Retrofits for Library Reading Rooms|Samira Ahmed; James Cole|10.5555/uni.me.2025.003|2025|Mechanical Engineering|Building and Environment||CROSSREF|VERIFIED",
            "Student Workshop Safety Incidents: A Ten-Year Review|Marco Rossi; Luis Chen|10.5555/uni.me.2026.006|2026|Mechanical Engineering|Safety Science|0000-0001-5100-0030|FORM|VERIFIED"
    };

    private final PublicationRepository publications;
    private final boolean seedEnabled;

    public PublicationDataSeeder(PublicationRepository publications,
                                 @org.springframework.beans.factory.annotation.Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.publications = publications;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled || publications.count() > 0) {
            return;
        }
        Instant newest = Instant.parse("2026-11-18T09:00:00Z");
        List<Publication> batch = new ArrayList<>();
        for (int i = 0; i < ROWS.length; i++) {
            String[] parts = ROWS[i].split("\\|", -1);
            if (parts.length != 9) {
                throw new IllegalStateException("Seed row has " + parts.length + " columns: " + ROWS[i]);
            }
            Publication publication = new Publication();
            publication.setTitle(parts[0]);
            publication.setAuthors(new ArrayList<>(Arrays.stream(parts[1].split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList()));
            publication.setDoi(parts[2]);
            publication.setYear(Integer.valueOf(parts[3]));
            publication.setDepartment(parts[4]);
            publication.setJournal(parts[5].isBlank() ? null : parts[5]);
            publication.setOrcidId(parts[6].isBlank() ? null : parts[6]);
            publication.setSource(PublicationSource.valueOf(parts[7]));
            publication.setStatus(PublicationStatus.valueOf(parts[8]));
            publication.setCreatedAt(newest.minus(Duration.ofHours(i * 8L)));
            batch.add(publication);
        }
        publications.saveAll(batch);
        log.info("Seeded {} sample publications", batch.size());
    }
}
