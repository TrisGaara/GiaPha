package vn.giapha.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.giapha.common.ApiException;
import vn.giapha.event.EventDtos.*;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.lunar.LunarCalendar;
import vn.giapha.lunar.LunarDate;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final FamilyMemberRepository memberRepository;
    private final PermissionService permissionService;
    private final vn.giapha.privacy.PrivacyService privacyService;

    @Value("${app.timezone}")
    private String timezone;
    @Value("${app.reminder.default-days-before}")
    private String defaultRemindDays;

    public LocalDate today() {
        return LocalDate.now(ZoneId.of(timezone));
    }

    // ---------- CRUD ----------

    @Transactional(readOnly = true)
    public List<EventDto> list(Long familyId) {
        permissionService.require(familyId, FamilyRoleType.VIEWER);
        List<Event> events = eventRepository.findByFamilyIdOrderByCreatedAtDesc(familyId);
        Map<Long, FamilyMember> members = memberRepository.findAllById(events.stream().map(Event::getMemberId)
                        .filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        LocalDate today = today();
        return events.stream().map(e -> toDto(e, members.get(e.getMemberId()), today)).toList();
    }

    @Transactional
    public EventDto create(Long familyId, EventRequest req) {
        PermissionService.Access a = permissionService.require(familyId, FamilyRoleType.BRANCH_MANAGER);
        Event e = new Event();
        e.setFamilyId(familyId);
        e.setCreatedBy(a.userId());
        apply(e, req, familyId);
        eventRepository.save(e);
        return toDto(e, e.getMemberId() == null ? null : memberRepository.findById(e.getMemberId()).orElse(null), today());
    }

    @Transactional
    public EventDto update(Long familyId, Long id, EventRequest req) {
        permissionService.require(familyId, FamilyRoleType.BRANCH_MANAGER);
        Event e = find(familyId, id);
        apply(e, req, familyId);
        return toDto(e, e.getMemberId() == null ? null : memberRepository.findById(e.getMemberId()).orElse(null), today());
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        permissionService.require(familyId, FamilyRoleType.BRANCH_MANAGER);
        eventRepository.delete(find(familyId, id));
    }

    // ---------- Lich su kien ----------

    @Transactional(readOnly = true)
    public List<Occurrence> occurrences(Long familyId, LocalDate from, LocalDate to, boolean includeDeathAnniversaries) {
        vn.giapha.privacy.ViewerContext ctx = privacyService.contextFor(permissionService.require(familyId, FamilyRoleType.VIEWER));
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 400) {
            throw ApiException.badRequest("Khoảng thời gian không hợp lệ (tối đa 400 ngày)");
        }
        // Ngay gio tu dong: bo qua nguoi bi an voi nguoi xem, hoac bi che ngay mat/ngay gio
        return occurrencesInternal(familyId, from, to, includeDeathAnniversaries,
                m -> ctx.canSeeNode(m) && !ctx.maskedFields(m).contains(vn.giapha.privacy.MaskedField.DEATH_DATE));
    }

    /** Khong kiem tra quyen - dung cho bo nhac lich chay nen (loc theo {@link Occurrence#audience()}) */
    @Transactional(readOnly = true)
    public List<Occurrence> occurrencesInternal(Long familyId, LocalDate from, LocalDate to, boolean includeDeathAnniversaries) {
        return occurrencesInternal(familyId, from, to, includeDeathAnniversaries, m -> true);
    }

    private List<Occurrence> occurrencesInternal(Long familyId, LocalDate from, LocalDate to, boolean includeDeathAnniversaries,
                                                 java.util.function.Predicate<FamilyMember> anniversaryFilter) {
        LocalDate today = today();
        List<Occurrence> result = new ArrayList<>();
        for (Event e : eventRepository.findByFamilyId(familyId)) {
            LocalDate d = nextOccurrence(e, from);
            while (d != null && !d.isAfter(to)) {
                result.add(new Occurrence(e.getId(), e.getMemberId(), e.getTitle(), e.getType(), e.getCalendarType(), d,
                        LunarCalendar.solarToLunar(d).label(), ChronoUnit.DAYS.between(today, d), e.getLocation(),
                        e.getDescription(), e.getRemindDaysBefore(), false, FamilyRoleType.VIEWER));
                d = e.isRecurring() ? nextOccurrence(e, d.plusDays(1)) : null;
            }
        }
        if (includeDeathAnniversaries) {
            // Tu dong sinh ngay gio tu ho so nhung nguoi da mat co ngay mat am lich
            for (FamilyMember m : memberRepository.findByFamilyIdAndAliveFalseAndDeathLunarDayNotNull(familyId)) {
                if (m.getDeathLunarMonth() == null || !anniversaryFilter.test(m)) {
                    continue;
                }
                LocalDate d = nextLunar(m.getDeathLunarDay(), m.getDeathLunarMonth(), from);
                while (d != null && !d.isAfter(to)) {
                    String title = "Giỗ " + m.getFullName() + (m.getGeneration() != null ? " (đời " + m.getGeneration() + ")" : "");
                    result.add(new Occurrence(null, m.getId(), title, EventType.DEATH_ANNIVERSARY, CalendarType.LUNAR, d,
                            LunarCalendar.solarToLunar(d).label(), ChronoUnit.DAYS.between(today, d), m.getBurialPlace(),
                            null, null, true, anniversaryAudience(m)));
                    d = nextLunar(m.getDeathLunarDay(), m.getDeathLunarMonth(), d.plusDays(1));
                }
            }
        }
        result.sort(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::title));
        return result;
    }

    /**
     * Ai duoc biet ngay gio tu dong cua mot nguoi:
     * an khoi cay -> chi Ban quan tri; an ngay mat / ho so khong cong khai -> tu Thanh vien tro len; con lai -> moi nguoi.
     */
    static FamilyRoleType anniversaryAudience(FamilyMember m) {
        if (!m.isVisibleOnTree()) {
            return FamilyRoleType.ADMIN;
        }
        return m.isHideDeathDate() || !m.isPublicProfile() ? FamilyRoleType.MEMBER : FamilyRoleType.VIEWER;
    }

    public Set<Integer> remindDays(String spec) {
        String s = StringUtils.hasText(spec) ? spec : defaultRemindDays;
        Set<Integer> days = new TreeSet<>();
        for (String p : s.split(",")) {
            if (StringUtils.hasText(p)) {
                days.add(Integer.parseInt(p.trim()));
            }
        }
        return days;
    }

    /** Ngay dien ra gan nhat ke tu (va bao gom) ngay from, hoac null neu khong con */
    static LocalDate nextOccurrence(Event e, LocalDate from) {
        if (e.getCalendarType() == CalendarType.SOLAR) {
            LocalDate base = e.getSolarDate();
            if (base == null) {
                return null;
            }
            if (!e.isRecurring()) {
                return base.isBefore(from) ? null : base;
            }
            LocalDate d = safeDate(from.getYear(), base.getMonthValue(), base.getDayOfMonth());
            return d.isBefore(from) ? safeDate(from.getYear() + 1, base.getMonthValue(), base.getDayOfMonth()) : d;
        }
        if (e.getLunarDay() == null || e.getLunarMonth() == null) {
            return null;
        }
        if (!e.isRecurring()) {
            if (e.getLunarYear() == null) {
                return null;
            }
            LocalDate d = LunarCalendar.lunarToSolarLenient(e.getLunarDay(), e.getLunarMonth(), e.getLunarYear());
            return d == null || d.isBefore(from) ? null : d;
        }
        return nextLunar(e.getLunarDay(), e.getLunarMonth(), from);
    }

    static LocalDate nextLunar(int lunarDay, int lunarMonth, LocalDate from) {
        LunarDate cur = LunarCalendar.solarToLunar(from);
        for (int y = cur.year(); y <= cur.year() + 2; y++) {
            LocalDate d = LunarCalendar.lunarToSolarLenient(lunarDay, lunarMonth, y);
            if (d != null && !d.isBefore(from)) {
                return d;
            }
        }
        return null;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        LocalDate first = LocalDate.of(year, month, 1);
        return first.withDayOfMonth(Math.min(day, first.lengthOfMonth()));
    }

    // ---------- Helpers ----------

    private Event find(Long familyId, Long id) {
        return eventRepository.findByIdAndFamilyId(id, familyId).orElseThrow(() -> ApiException.notFound("sự kiện"));
    }

    private void apply(Event e, EventRequest req, Long familyId) {
        if (req.calendarType() == CalendarType.SOLAR && req.solarDate() == null) {
            throw ApiException.badRequest("Cần nhập ngày dương lịch");
        }
        if (req.calendarType() == CalendarType.LUNAR && (req.lunarDay() == null || req.lunarMonth() == null)) {
            throw ApiException.badRequest("Cần nhập ngày và tháng âm lịch");
        }
        boolean recurring = req.recurring() == null || req.recurring();
        if (req.calendarType() == CalendarType.LUNAR && !recurring && req.lunarYear() == null) {
            throw ApiException.badRequest("Sự kiện âm lịch không lặp lại cần có năm âm lịch");
        }
        if (req.memberId() != null) {
            memberRepository.findByIdAndFamilyId(req.memberId(), familyId)
                    .orElseThrow(() -> ApiException.notFound("thành viên"));
        }
        e.setTitle(req.title().trim());
        e.setDescription(req.description());
        e.setType(req.type());
        e.setCalendarType(req.calendarType());
        e.setSolarDate(req.calendarType() == CalendarType.SOLAR ? req.solarDate() : null);
        e.setLunarDay(req.calendarType() == CalendarType.LUNAR ? req.lunarDay() : null);
        e.setLunarMonth(req.calendarType() == CalendarType.LUNAR ? req.lunarMonth() : null);
        e.setLunarYear(req.calendarType() == CalendarType.LUNAR ? req.lunarYear() : null);
        e.setRecurring(recurring);
        e.setLocation(req.location());
        e.setMemberId(req.memberId());
        e.setRemindDaysBefore(StringUtils.hasText(req.remindDaysBefore()) ? req.remindDaysBefore().replace(" ", "") : null);
    }

    private EventDto toDto(Event e, FamilyMember m, LocalDate today) {
        return new EventDto(e.getId(), e.getFamilyId(), e.getMemberId(), m == null ? null : m.getFullName(), e.getTitle(),
                e.getDescription(), e.getType(), e.getCalendarType(), e.getSolarDate(), e.getLunarDay(), e.getLunarMonth(),
                e.getLunarYear(), e.isRecurring(), e.getLocation(), e.getRemindDaysBefore(), nextOccurrence(e, today),
                e.getCreatedAt());
    }
}
