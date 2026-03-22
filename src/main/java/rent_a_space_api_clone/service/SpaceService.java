package rent_a_space_api_clone.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.CreateSpaceRequest;
import rent_a_space_api_clone.dto.SpaceResponse;
import rent_a_space_api_clone.dto.UpdateSpaceRequest;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.exception.ResourceNotFoundException;
import rent_a_space_api_clone.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Handler;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;
    private final SpacesImageRepository spacesImageRepository;
    private final HolidayRuleRepository holidayRuleRepository;
    private final HolidayOverrideRepository holidayOverrideRepository;
    private final UserProfileRepository userProfileRepository;

    private static final Map<String, Integer> DAY_TO_INDEX = Map.of(
            "Sun", 0, "Mon", 1, "Tue", 2, "Wed", 3, "Thu", 4, "Fri", 5, "Sat", 6
    );
    private final EntityManager entityManager;

    @Transactional
    public Long createSpace(CreateSpaceRequest request) {
        // Get current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserProfile hostProfile = userProfileRepository.findByUserEmailAndRole(email, Role.HOST)
                .orElseThrow(() -> new IllegalStateException("Host profile not found"));

        // Find category
        Category category = categoryRepository.findByName(request.getCategory())
                .orElseThrow(() -> new IllegalArgumentException("Invalid category"));

        // Create space
        Space space = new Space();
        space.setName(request.getName());
        space.setDescription(request.getDescription());
        space.setHostProfile(hostProfile);
        space.setCategory(category);
        space.setPhone1(request.getPhone1());
        space.setPhone2(request.getPhone2());
        space.setEmail(request.getEmail());
        space.setIsClosedAtPublicHolidays(request.getIsClosedOnPublicHolidays());
        space.setIsVisible(request.getIsVisible() != null ? request.getIsVisible() : false);

        // Handle times
        if (Boolean.TRUE.equals(request.getIsOpen24())) {
            space.setCloseStart(LocalTime.of(0, 0, 0));
            space.setCloseEnd(LocalTime.of(0, 0, 0));
        } else if (request.getOpensAt() != null && request.getClosesAt() != null) {
            space.setCloseStart(LocalTime.parse(request.getClosesAt()));
            space.setCloseEnd(LocalTime.parse(request.getOpensAt()));
        }

        // Save space first to get id
        Space savedSpace = spaceRepository.save(space);

        // Handle images
        List<SpacesImage> spacesImages = new ArrayList<>();
        int order = 1;
        if (request.getMainImageUrl() != null) {
            Image image = imageRepository.findByFullUrl(request.getMainImageUrl())
                    .orElseThrow(() -> new IllegalArgumentException("Image not found: " + request.getMainImageUrl()));
            SpacesImage si = new SpacesImage();
            si.setSpace(savedSpace);
            si.setImage(image);
            si.setOrderSeq(order++);
            spacesImages.add(si);
        }
        if (request.getImagesUrls() != null) {
            for (String url : request.getImagesUrls()) {
                Image image = imageRepository.findByFullUrl(url)
                        .orElseThrow(() -> new IllegalArgumentException("Image not found: " + url));
                SpacesImage si = new SpacesImage();
                si.setSpace(savedSpace);
                si.setImage(image);
                si.setOrderSeq(order++);
                spacesImages.add(si);
            }
        }
        spacesImageRepository.saveAll(spacesImages);

        // Handle holiday rules
        if (request.getClosesOnEvery() != null) {
            HolidayRule rule = new HolidayRule();
            rule.setSpace(savedSpace);
            String type = request.getClosesOnEvery().getType();
            switch (type) {
                case "every_week" -> {
                    rule.setFrequencyType("WEEKLY");
                }
                case "every_odd_week" -> {
                    rule.setFrequencyType("BI_WEEKLY");
                    rule.setNthOccurrence((short) 1);
                }
                case "every_even_week" -> {
                    rule.setFrequencyType("BI_WEEKLY");
                    rule.setNthOccurrence((short) 0);
                }
                case "every_first_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 1);
                }
                case "every_second_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 2);
                }
                case "every_third_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 3);
                }
                case "every_fourth_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 4);
                }
                case "every_last_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) -1);
                }
                case "every_last_day_of_month" -> {
                    rule.setFrequencyType("LAST_DAY_OF_MONTH");
                    rule.setDayMask((short) 127);
                }
                case "every_month" -> {
                    rule.setFrequencyType("MONTHLY_FIXED_DATE");
                    rule.setDayMask((short) 127);
                    if (request.getClosesOnEvery().getDays() != null && !request.getClosesOnEvery().getDays().isEmpty()) {
                        rule.setNthOccurrence(Short.parseShort(request.getClosesOnEvery().getDays().get(0)));
                    }
                }
            }
            if (request.getClosesOnEvery().getDays() != null &&
                    !"every_last_day_of_month".equals(type) &&
                    !"every_month".equals(type)) {
                short mask = 0;
                for (String day : request.getClosesOnEvery().getDays()) {
                    Integer index = DAY_TO_INDEX.get(day);
                    if (index != null) {
                        mask |= (short) (1 << index);
                    }
                }
                rule.setDayMask(mask);
            }
            holidayRuleRepository.save(rule);
        }

        // Handle holiday overrides
        if (request.getClosesOn() != null) {
            for (CreateSpaceRequest.ClosesOn closesOnItem : request.getClosesOn()) {
                HolidayOverride override = new HolidayOverride();
                override.setSpace(savedSpace);
                override.setName(closesOnItem.getName());
                override.setStartsAt(LocalDate.parse(closesOnItem.getStartDate()));
                override.setEndsAt(LocalDate.parse(closesOnItem.getLastDate()));
                override.setIsClosed(true);
                if (closesOnItem.getDays() != null) {
                    short mask = 0;
                    for (String day : closesOnItem.getDays()) {
                        Integer index = DAY_TO_INDEX.get(day);
                        if (index != null) {
                        mask |= (short) (1 << index);
                        }
                    }
                    override.setDayMask(mask);
                }
                override.setPriorityWeight(0); // default
                holidayOverrideRepository.save(override);
            }
        }
        entityManager.flush();
        entityManager.clear();
        return savedSpace.getId();
    }

    @Transactional
    public void updateSpace(Long spaceId, UpdateSpaceRequest request) {
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("Space not found with id: " + spaceId));

        // Update simple fields
        if (request.getCategory() != null) {
            Category category = categoryRepository.findByName(request.getCategory())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid category"));
            space.setCategory(category);
        }
        if (request.getName() != null) {
            space.setName(request.getName());
        }
        if (request.getDescription() != null) {
            space.setDescription(request.getDescription());
        }
        if (request.getPhone1() != null) {
            space.setPhone1(request.getPhone1());
        }
        if (request.getPhone2() != null) {
            space.setPhone2(request.getPhone2());
        }
        if (request.getEmail() != null) {
            space.setEmail(request.getEmail());
        }
        if (request.getIsClosedOnPublicHolidays() != null) {
            space.setIsClosedAtPublicHolidays(request.getIsClosedOnPublicHolidays());
        }
        if (request.getIsVisible() != null) {
            space.setIsVisible(request.getIsVisible());
        }

        // Handle times
        Boolean isOpen24 = request.getIsOpen24();
        String opensAt = request.getOpensAt();
        String closesAt = request.getClosesAt();
        if (isOpen24 != null || (opensAt != null && closesAt != null)) {
            if (Boolean.TRUE.equals(isOpen24)) {
                space.setCloseStart(LocalTime.of(0, 0, 0));
                space.setCloseEnd(LocalTime.of(0, 0, 0));
            } else if (opensAt != null && closesAt != null) {
                space.setCloseStart(LocalTime.parse(closesAt));
                space.setCloseEnd(LocalTime.parse(opensAt));
            }
        }

        // Handle images
        if (request.getMainImageUrl() != null || request.getImagesUrls() != null) {
            // Clear existing
            spacesImageRepository.deleteBySpace(space);
            List<SpacesImage> spacesImages = new ArrayList<>();
            int order = 1;
            if (request.getMainImageUrl() != null) {
                Image image = imageRepository.findByFullUrl(request.getMainImageUrl())
                        .orElseThrow(() -> new IllegalArgumentException("Image not found: " + request.getMainImageUrl()));
                SpacesImage si = new SpacesImage();
                si.setSpace(space);
                si.setImage(image);
                si.setOrderSeq(order++);
                spacesImages.add(si);
            }
            if (request.getImagesUrls() != null) {
                for (String url : request.getImagesUrls()) {
                    Image image = imageRepository.findByFullUrl(url)
                            .orElseThrow(() -> new IllegalArgumentException("Image not found: " + url));
                    SpacesImage si = new SpacesImage();
                    si.setSpace(space);
                    si.setImage(image);
                    si.setOrderSeq(order++);
                    spacesImages.add(si);
                }
            }
            spacesImageRepository.saveAll(spacesImages);
        }

        // Handle holiday rules
        if (request.getClosesOnEvery() != null) {
            // Clear existing
            holidayRuleRepository.deleteBySpace(space);
            HolidayRule rule = new HolidayRule();
            rule.setSpace(space);
            String type = request.getClosesOnEvery().getType();
            switch (type) {
                case "every_week" -> {
                    rule.setFrequencyType("WEEKLY");
                }
                case "every_odd_week" -> {
                    rule.setFrequencyType("BI_WEEKLY");
                    rule.setNthOccurrence((short) 1);
                }
                case "every_even_week" -> {
                    rule.setFrequencyType("BI_WEEKLY");
                    rule.setNthOccurrence((short) 0);
                }
                case "every_first_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 1);
                }
                case "every_second_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 2);
                }
                case "every_third_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 3);
                }
                case "every_fourth_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) 4);
                }
                case "every_last_week" -> {
                    rule.setFrequencyType("MONTHLY_CALENDAR_WEEK");
                    rule.setNthOccurrence((short) -1);
                }
                case "every_last_day_of_month" -> {
                    rule.setFrequencyType("LAST_DAY_OF_MONTH");
                    rule.setDayMask((short) 127);
                }
                case "every_month" -> {
                    rule.setFrequencyType("MONTHLY_FIXED_DATE");
                    rule.setDayMask((short) 127);
                    if (request.getClosesOnEvery().getDays() != null && !request.getClosesOnEvery().getDays().isEmpty()) {
                        rule.setNthOccurrence(Short.parseShort(request.getClosesOnEvery().getDays().get(0)));
                    }
                }
            }
            if (request.getClosesOnEvery().getDays() != null &&
                    !"every_last_day_of_month".equals(type) &&
                    !"every_month".equals(type)) {
                short mask = 0;
                for (String day : request.getClosesOnEvery().getDays()) {
                    Integer index = DAY_TO_INDEX.get(day);
                    if (index != null) {
                        mask |= (short) (1 << index);
                    }
                }
                rule.setDayMask(mask);
            }
            holidayRuleRepository.save(rule);
        }

        // Handle holiday overrides
        if (request.getClosesOn() != null) {
            // Clear existing
            holidayOverrideRepository.deleteBySpace(space);
            List<HolidayOverride> overrides = new ArrayList<>();
            for (UpdateSpaceRequest.ClosesOn closesOnItem : request.getClosesOn()) {
                HolidayOverride override = new HolidayOverride();
                override.setSpace(space);
                override.setName(closesOnItem.getName());
                override.setStartsAt(LocalDate.parse(closesOnItem.getStartDate()));
                override.setEndsAt(LocalDate.parse(closesOnItem.getLastDate()));
                override.setIsClosed(true);
                if (closesOnItem.getDays() != null) {
                    short mask = 0;
                    for (String day : closesOnItem.getDays()) {
                        Integer index = DAY_TO_INDEX.get(day);
                        if (index != null) {
                            mask |= (short) (1 << index);
                        }
                    }
                    override.setDayMask(mask);
                }
                override.setPriorityWeight(0); // default
                overrides.add(override);
            }
            holidayOverrideRepository.saveAll(overrides);
        }

        spaceRepository.save(space);
        entityManager.flush();
        entityManager.clear();
    }

    public SpaceResponse buildSpaceResponse(Long spaceId) {
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("Space not found with id: " + spaceId));
        String id = String.valueOf(space.getId());
        String category = space.getCategory().getName();
        String name = space.getName();
        String description = space.getDescription();
        Boolean is_open_24 = space.getCloseStart() == null ? null :
                space.getCloseStart().equals(space.getCloseEnd());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String opens_at = space.getCloseEnd() == null? null :
                space.getCloseEnd().format(formatter);
        String closes_at = space.getCloseStart() == null ? null :
                space.getCloseStart().format(formatter);
        String main_image_url = space.getImages().stream().filter(si -> si.getOrderSeq() == 1).findFirst().map(si -> si.getImage().getFullUrl()).orElse(null);
        List<String> images_urls = space.getImages().stream().sorted(Comparator.comparing(SpacesImage::getOrderSeq)).skip(1).map(si -> si.getImage().getFullUrl()).toList();
        String phone1 = space.getPhone1();
        String phone2 = space.getPhone2();
        String email = space.getEmail();
        Boolean is_closed_on_public_holidays = space.getIsClosedAtPublicHolidays();
        Boolean is_visible = space.getIsVisible();
        SpaceResponse.ClosesOnEvery closes_on_every = null;
        if (space.getHolidayRules() != null && !space.getHolidayRules().isEmpty()) {
            HolidayRule rule = space.getHolidayRules().get(0);
            String type = switch (rule.getFrequencyType()) {
                case "WEEKLY" -> "every_week";
                case "BI_WEEKLY" -> rule.getNthOccurrence() == 1 ? "every_odd_week" : "every_even_week";
                case "MONTHLY_CALENDAR_WEEK" -> switch (rule.getNthOccurrence().intValue()) {
                    case 1 -> "every_first_week";
                    case 2 -> "every_second_week";
                    case 3 -> "every_third_week";
                    case 4 -> "every_fourth_week";
                    case -1 -> "every_last_week";
                    default -> null;
                };
                case "LAST_DAY_OF_MONTH" -> "every_last_day_of_month";
                case "MONTHLY_FIXED_DATE" -> "every_month";
                default -> null;
            };
            List<String> days = decodeDays(rule.getDayMask());
            closes_on_every = new SpaceResponse.ClosesOnEvery(type, days);
        }
        List<SpaceResponse.ClosesOn> closes_on = space.getHolidayOverrides().stream()
                .map(override -> new SpaceResponse.ClosesOn(override.getName(), override.getStartsAt().toString(), override.getEndsAt().toString(), decodeDays(override.getDayMask())))
                .toList();
        SpaceResponse.SpaceData data = new SpaceResponse.SpaceData(id, category, name, description, is_open_24, opens_at, closes_at, main_image_url, images_urls, phone1, phone2, email, is_closed_on_public_holidays, closes_on_every, closes_on, is_visible);
        return new SpaceResponse(data);
    }

    private List<String> decodeDays(short mask) {
        List<String> daysList = new ArrayList<>();
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                daysList.add(dayNames[i]);
            }
        }
        return daysList;
    }

    public boolean isSpaceOwner(Long id, String username) {
        return spaceRepository.findById(id)
                .map((space) -> space.getHostProfile().getUser()
                        .getEmail().equals(username))
                .orElse(false);
    }
}