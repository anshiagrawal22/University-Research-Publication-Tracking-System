package edu.university.portal.repository;

import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PublicationRepository extends JpaRepository<Publication, Long> {

    boolean existsByDoiIgnoreCase(String doi);

    boolean existsByDoiIgnoreCaseAndIdNot(String doi, Long id);

    @Query("SELECT DISTINCT p.department FROM Publication p ORDER BY p.department ASC")
    List<String> findDistinctDepartments();

    @Query("SELECT DISTINCT p.year FROM Publication p ORDER BY p.year DESC")
    List<Integer> findDistinctYears();

    @Query("SELECT DISTINCT a FROM Publication p JOIN p.authors a ORDER BY a ASC")
    List<String> findDistinctAuthors();

    @Query(
            value = """
                    SELECT p FROM Publication p
                    WHERE p.id IN (
                        SELECT DISTINCT p2.id FROM Publication p2
                        LEFT JOIN p2.authors a
                        WHERE (:applyStatus = false OR p2.status = :status)
                          AND (:applyDepartment = false OR LOWER(p2.department) = LOWER(:department))
                          AND (:applyFaculty = false OR EXISTS (
                                SELECT af FROM p2.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
                          ))
                          AND (:applyYear = false OR p2.year = :year)
                          AND (:applyJournal = false OR LOWER(COALESCE(p2.journal, '')) LIKE LOWER(CONCAT('%', :journal, '%')))
                          AND (:applySearch = false
                               OR LOWER(p2.title) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(COALESCE(p2.doi, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(a) LIKE LOWER(CONCAT('%', :search, '%')))
                    )
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Publication p
                    WHERE p.id IN (
                        SELECT DISTINCT p2.id FROM Publication p2
                        LEFT JOIN p2.authors a
                        WHERE (:applyStatus = false OR p2.status = :status)
                          AND (:applyDepartment = false OR LOWER(p2.department) = LOWER(:department))
                          AND (:applyFaculty = false OR EXISTS (
                                SELECT af FROM p2.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
                          ))
                          AND (:applyYear = false OR p2.year = :year)
                          AND (:applyJournal = false OR LOWER(COALESCE(p2.journal, '')) LIKE LOWER(CONCAT('%', :journal, '%')))
                          AND (:applySearch = false
                               OR LOWER(p2.title) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(COALESCE(p2.doi, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(a) LIKE LOWER(CONCAT('%', :search, '%')))
                    )
                    """)
    Page<Publication> search(
            @Param("applyStatus") boolean applyStatus,
            @Param("status") PublicationStatus status,
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyYear") boolean applyYear,
            @Param("year") int year,
            @Param("applyJournal") boolean applyJournal,
            @Param("journal") String journal,
            @Param("applySearch") boolean applySearch,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT COUNT(p) FROM Publication p
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            """)
    long countVerified(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT COUNT(DISTINCT p.department) FROM Publication p
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            """)
    long countVerifiedDepartments(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT COUNT(DISTINCT a) FROM Publication p JOIN p.authors a
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            """)
    long countVerifiedAuthors(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT p.department, COUNT(p) FROM Publication p
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            GROUP BY p.department
            ORDER BY COUNT(p) DESC, p.department ASC
            """)
    List<Object[]> countVerifiedByDepartment(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT p.year, COUNT(p) FROM Publication p
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            GROUP BY p.year
            ORDER BY p.year ASC
            """)
    List<Object[]> countVerifiedByYear(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT a, COUNT(p) FROM Publication p JOIN p.authors a
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            GROUP BY a
            ORDER BY COUNT(p) DESC, a ASC
            """)
    List<Object[]> countVerifiedByAuthor(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear);

    @Query("""
            SELECT p FROM Publication p
            WHERE p.status = edu.university.portal.model.PublicationStatus.VERIFIED
              AND (:applyDepartment = false OR LOWER(p.department) = LOWER(:department))
              AND (:applyFaculty = false OR EXISTS (
                    SELECT af FROM p.authors af WHERE LOWER(af) LIKE LOWER(CONCAT('%', :faculty, '%'))
              ))
              AND (:applyFrom = false OR p.year >= :fromYear)
              AND (:applyTo = false OR p.year <= :toYear)
            ORDER BY p.createdAt DESC
            """)
    List<Publication> findRecentVerified(
            @Param("applyDepartment") boolean applyDepartment,
            @Param("department") String department,
            @Param("applyFaculty") boolean applyFaculty,
            @Param("faculty") String faculty,
            @Param("applyFrom") boolean applyFrom,
            @Param("fromYear") int fromYear,
            @Param("applyTo") boolean applyTo,
            @Param("toYear") int toYear,
            Pageable pageable);
}
