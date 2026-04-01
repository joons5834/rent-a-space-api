package rent_a_space_api_clone.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.*;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.exception.ResourceNotFoundException;
import rent_a_space_api_clone.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static rent_a_space_api_clone.enums.HolidayFrequencyType.*;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;
    private final SpaceImageRepository spaceImageRepository;
    private final HolidayRuleRepository holidayRuleRepository;
    private final HolidayOverrideRepository holidayOverrideRepository;
    private final UserProfileRepository userProfileRepository;
    private final SubspaceRepository subspaceRepository;
    private final SubspaceImageRepository subspaceImageRepository;

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
        Category category = categoryRepository.findByName(request.category())
                .orElseThrow(() -> new IllegalArgumentException("Invalid category"));

        // Create space
        Space space = new Space();
        space.setName(request.name());
        space.setDescription(request.description());
        space.setHostProfile(hostProfile);
        space.setCategory(category);
        space.setPhone1(request.phone1());
        space.setPhone2(request.phone2());
        space.setEmail(request.email());
        space.setIsClosedAtPublicHolidays(request.isClosedOnPublicHolidays());
        space.setIsVisible(request.isVisible() != null ? request.isVisible() : false);

        ZoneId timezone = request.timezone();
        space.setTimezone(timezone);

        // Handle times
        configureOpeningTimes(space, request.isOpen24(), request.opensAt(), request.closesAt());

        // Save space first to get id
        Space savedSpace = spaceRepository.save(space);

        // Handle images
        createAndSaveSpacesImages(savedSpace, request.mainImageUrl(), request.imagesUrls());

        // Handle holiday rules
        if (request.closesOnEvery() != null) {
            HolidayRule rule = configureHolidayRule(savedSpace, request.closesOnEvery().type(), request.closesOnEvery().days());
            holidayRuleRepository.save(rule);
        }

        // Handle holiday overrides
        if (request.closesOn() != null) {
            processHolidayOverrides(savedSpace, request.closesOn());
        }
        entityManager.flush();
        entityManager.clear();
        return savedSpace.getId();
    }

    @Transactional
    public void updateSpace(Long spaceId, UpdateSpaceRequest request) {
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("Space not found with id: " + spaceId));

        // Update simple fields
        if (request.category() != null) {
            Category category = categoryRepository.findByName(request.category())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid category"));
            space.setCategory(category);
        }
        if (request.name() != null) {
            space.setName(request.name());
        }
        if (request.description() != null) {
            space.setDescription(request.description());
        }
        if (request.phone1() != null) {
            space.setPhone1(request.phone1());
        }
        if (request.phone2() != null) {
            space.setPhone2(request.phone2());
        }
        if (request.email() != null) {
            space.setEmail(request.email());
        }
        if (request.isClosedOnPublicHolidays() != null) {
            space.setIsClosedAtPublicHolidays(request.isClosedOnPublicHolidays());
        }
        if (request.isVisible() != null) {
            space.setIsVisible(request.isVisible());
        }
        if (request.timezone() != null) {
            space.setTimezone(request.timezone());
        }

        // Handle times
        configureOpeningTimes(space, request.isOpen24(), request.opensAt(), request.closesAt());

        // Handle images
        if (request.mainImageUrl() != null || request.imagesUrls() != null) {
            // Clear existing
            spaceImageRepository.deleteBySpace(space);
            createAndSaveSpacesImages(space, request.mainImageUrl(), request.imagesUrls());
        }

        // Handle holiday rules
        if (request.closesOnEvery() != null) {
            // Clear existing
            holidayRuleRepository.deleteBySpace(space);
            HolidayRule rule = configureHolidayRule(space, request.closesOnEvery().type(),
                    request.closesOnEvery().days());
            holidayRuleRepository.save(rule);
        }

        // Handle holiday overrides
        if (request.closesOn() != null) {
            // Clear existing
            holidayOverrideRepository.deleteBySpace(space);
            processHolidayOverridesUpdate(space, request.closesOn());
        }

        spaceRepository.save(space);
        entityManager.flush();
        entityManager.clear();
    }

    private void configureOpeningTimes(Space space, Boolean isOpen24, String opensAt, String closesAt) {
        if (Boolean.TRUE.equals(isOpen24)) {
            space.setCloseStart(LocalTime.of(0, 0, 0));
            space.setCloseEnd(LocalTime.of(0, 0, 0));
        } else if (opensAt != null && closesAt != null) {
            space.setCloseStart(LocalTime.parse(closesAt));
            space.setCloseEnd(LocalTime.parse(opensAt));
        }
    }

    private void createAndSaveSpacesImages(Space space, String mainImageUrl, List<String> imagesUrls) {
        List<SpaceImage> spaceImages = new ArrayList<>();
        int order = 1;
        if (mainImageUrl != null) {
            Image image = imageRepository.findByFullUrl(mainImageUrl)
                    .orElseThrow(() -> new IllegalArgumentException("Image not found: " + mainImageUrl));
            SpaceImage si = new SpaceImage();
            si.setSpace(space);
            si.setImage(image);
            si.setOrderSeq(order++);
            spaceImages.add(si);
        }
        if (imagesUrls != null) {
            for (String url : imagesUrls) {
                Image image = imageRepository.findByFullUrl(url)
                        .orElseThrow(() -> new IllegalArgumentException("Image not found: " + url));
                SpaceImage si = new SpaceImage();
                si.setSpace(space);
                si.setImage(image);
                si.setOrderSeq(order++);
                spaceImages.add(si);
            }
        }
        spaceImageRepository.saveAll(spaceImages);
    }

    private HolidayRule configureHolidayRule(Space space, String type, List<String> days) {
        HolidayRule rule = new HolidayRule();
        rule.setSpace(space);
        switch (type) {
            case "every_week" -> rule.setFrequencyType(WEEKLY);
            case "every_odd_week" -> {
                rule.setFrequencyType(BI_WEEKLY);
                rule.setNthOccurrence((short) 1);
            }
            case "every_even_week" -> {
                rule.setFrequencyType(BI_WEEKLY);
                rule.setNthOccurrence((short) 0);
            }
            case "every_first_week" -> {
                rule.setFrequencyType(MONTHLY_CALENDAR_WEEK);
                rule.setNthOccurrence((short) 1);
            }
            case "every_second_week" -> {
                rule.setFrequencyType(MONTHLY_CALENDAR_WEEK);
                rule.setNthOccurrence((short) 2);
            }
            case "every_third_week" -> {
                rule.setFrequencyType(MONTHLY_CALENDAR_WEEK);
                rule.setNthOccurrence((short) 3);
            }
            case "every_fourth_week" -> {
                rule.setFrequencyType(MONTHLY_CALENDAR_WEEK);
                rule.setNthOccurrence((short) 4);
            }
            case "every_last_week" -> {
                rule.setFrequencyType(MONTHLY_CALENDAR_WEEK);
                rule.setNthOccurrence((short) -1);
            }
            case "every_last_day_of_month" -> {
                rule.setFrequencyType(LAST_DAY_OF_MONTH);
                rule.setDayMask((short) 127);
            }
            case "every_month" -> {
                rule.setFrequencyType(MONTHLY_FIXED_DATE);
                rule.setDayMask((short) 127);
                if (days != null && !days.isEmpty()) {
                    rule.setNthOccurrence(Short.parseShort(days.get(0)));
                }
            }
        }
        if (days != null &&
                !"every_last_day_of_month".equals(type) &&
                !"every_month".equals(type)) {
            rule.setDayMask(calculateDayMask(days));
        }
        return rule;
    }

    private short calculateDayMask(List<String> days) {
        short mask = 0;
        for (String day : days) {
            Integer index = DAY_TO_INDEX.get(day);
            if (index != null) {
                mask |= (short) (1 << index);
            }
        }
        return mask;
    }

    private void processHolidayOverrides(Space space, List<CreateSpaceRequest.ClosesOn> closesOn) {
        List<HolidayOverride> overrides = new ArrayList<>();
        for (CreateSpaceRequest.ClosesOn closesOnItem : closesOn) {
            HolidayOverride override = new HolidayOverride();
            override.setSpace(space);
            override.setName(closesOnItem.name());
            override.setStartsAt(LocalDate.parse(closesOnItem.startDate()));
            override.setEndsAt(LocalDate.parse(closesOnItem.lastDate()));
            override.setIsClosed(true);
            if (closesOnItem.days() != null) {
                override.setDayMask(calculateDayMask(closesOnItem.days()));
            }
            override.setPriorityWeight(0); // default
            overrides.add(override);
        }
        holidayOverrideRepository.saveAll(overrides);
    }

    private void processHolidayOverridesUpdate(Space space, List<UpdateSpaceRequest.ClosesOn> closesOn) {
        List<HolidayOverride> overrides = new ArrayList<>();
        for (UpdateSpaceRequest.ClosesOn closesOnItem : closesOn) {
            HolidayOverride override = new HolidayOverride();
            override.setSpace(space);
            override.setName(closesOnItem.name());
            override.setStartsAt(LocalDate.parse(closesOnItem.startDate()));
            override.setEndsAt(LocalDate.parse(closesOnItem.lastDate()));
            override.setIsClosed(true);
            if (closesOnItem.days() != null) {
                override.setDayMask(calculateDayMask(closesOnItem.days()));
            }
            override.setPriorityWeight(0); // default
            overrides.add(override);
        }
        holidayOverrideRepository.saveAll(overrides);
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
        List<String> images_urls = space.getImages().stream().sorted(Comparator.comparing(SpaceImage::getOrderSeq)).skip(1).map(si -> si.getImage().getFullUrl()).toList();
        String phone1 = space.getPhone1();
        String phone2 = space.getPhone2();
        String email = space.getEmail();
        Boolean is_closed_on_public_holidays = space.getIsClosedAtPublicHolidays();
        Boolean is_visible = space.getIsVisible();
        ZoneId timezone = space.getTimezone();
        SpaceResponse.ClosesOnEvery closes_on_every = null;
        if (space.getHolidayRules() != null && !space.getHolidayRules().isEmpty()) {
            HolidayRule rule = space.getHolidayRules().get(0);
            String type = switch (rule.getFrequencyType()) {
                case WEEKLY -> "every_week";
                case BI_WEEKLY -> rule.getNthOccurrence() == 1 ? "every_odd_week" : "every_even_week";
                case MONTHLY_CALENDAR_WEEK -> switch (rule.getNthOccurrence().intValue()) {
                    case 1 -> "every_first_week";
                    case 2 -> "every_second_week";
                    case 3 -> "every_third_week";
                    case 4 -> "every_fourth_week";
                    case -1 -> "every_last_week";
                    default -> null;
                };
                case LAST_DAY_OF_MONTH -> "every_last_day_of_month";
                case MONTHLY_FIXED_DATE -> "every_month";
                default -> null;
            };
            List<String> days = decodeDays(rule.getDayMask());
            closes_on_every = new SpaceResponse.ClosesOnEvery(type, days);
        }
        List<SpaceResponse.ClosesOn> closes_on = space.getHolidayOverrides().stream()
                .map(override -> new SpaceResponse.ClosesOn(override.getName(), override.getStartsAt().toString(), override.getEndsAt().toString(), decodeDays(override.getDayMask())))
                .toList();
        SpaceResponse.SpaceData data = new SpaceResponse.SpaceData(id, category, name, description, is_open_24, opens_at, closes_at, main_image_url, images_urls, phone1, phone2, email, is_closed_on_public_holidays, closes_on_every, closes_on, is_visible, timezone);
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

    @Transactional
    public Long createSubspace(Long id, CreateSubspaceRequest request) {
        Subspace subspace = new Subspace();
        subspace.setName(request.name());
        subspace.setDescription(request.description());
        subspace.setMinHours(request.minHours());
        subspace.setMaxHours(request.maxHours());
        subspace.setIsVisible(request.isVisible());
        Space space = spaceRepository.findById(id).orElseThrow();
        subspace.setSpace(space);
        Subspace savedSubspace = subspaceRepository.save(subspace);

        List<String> imageUrls = request.imagesUrls();
        if (imageUrls != null) {
            imageUrls.add(0, request.mainImageUrl());
            List<SubspaceImage> subspaceImages = createSubspaceImages(imageUrls,
                    subspace);
            subspaceImageRepository.saveAll(subspaceImages);
        }

        entityManager.flush();
        entityManager.clear();
        return savedSubspace.getId();
    }

    private List<SubspaceImage> createSubspaceImages(List<String> urls,
                                                     Subspace subspace) {
        List<SubspaceImage> subspaceImages = new ArrayList<>();
        int order = 0;
        for (String url : urls) {
            SubspaceImage subspaceImage = new SubspaceImage();
            subspaceImage.setSubspace(subspace);
            Image image = imageRepository.findByFullUrl(url).orElseThrow();
            subspaceImage.setImage(image);
            subspaceImage.setOrderSeq(order++);
            subspaceImages.add(subspaceImage);
        }
        return subspaceImages;
    }

    public SubspaceResponse buildSubspaceResponse(Long subspaceId) {
        Subspace subspace = subspaceRepository.findById(subspaceId).orElseThrow();
        Long id = subspace.getId();
        String name = subspace.getName();
        String description = subspace.getDescription();
        List<SubspaceImage> subspacesImages = subspace.getImages();
        String mainImageUrl = null;
        List<String> imageUrls = null;
        if (subspacesImages != null && !subspacesImages.isEmpty()) {
            mainImageUrl = subspacesImages
                    .get(0).getImage().getFullUrl();
            imageUrls = subspacesImages
                    .stream()
                    .skip(1)
                    .map((image) -> image.getImage().getFullUrl())
                    .toList();
        }
        Integer minHours = subspace.getMinHours();
        Integer maxHours = subspace.getMaxHours();
        Boolean isVisible = subspace.getIsVisible();
        SubspaceResponse.SubspaceData subspaceData =
                new SubspaceResponse.SubspaceData(id, name, description,
                mainImageUrl, imageUrls, minHours,
                maxHours, isVisible);
        return new SubspaceResponse(subspaceData);
    }
}