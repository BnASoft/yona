package models;

import models.enumeration.*;
import models.support.*;
import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;

import javax.persistence.*;
import java.text.*;
import java.util.*;

@Entity
public class Milestone extends Model {

    private static final long serialVersionUID = 1L;
    private static Finder<Long, Milestone> find = new Finder<Long, Milestone>(
            Long.class, Milestone.class);

    public static String DEFAULT_SORTER = "dueDate";

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    @Constraints.Required
    public String contents;

    public int numClosedIssues;
    public int numOpenIssues;
    public int numTotalIssues;
    public int completionRate;

    @ManyToOne
    public Project project;

    public static void create(Milestone milestone) {
        milestone.save();
    }

    public static void update(Milestone milestone, Long id) {
        int completionRate = 0;
        if (milestone.numTotalIssues != 0) {
            completionRate =
                    calculateCompletionRate(milestone.numTotalIssues, milestone.numClosedIssues);
        }
        milestone.completionRate = completionRate;
        milestone.update(id);
    }

    private static int calculateCompletionRate(int numTotalIssues, int numClosedIssues) {
        return new Double(((double) numClosedIssues / (double) numTotalIssues) * 100).intValue();
    }

    public static void delete(Milestone milestone) {
        List<Issue> issues = Issue.findByMilestoneId(milestone.id);
        milestone.delete();
        for (Issue issue : issues) {
            issue.milestoneId = null;
            issue.update();
        }
    }

    public static Milestone findById(Long id) {
        return find.byId(id);
    }

    /**
     * 해당 프로젝트의 전체 마일스톤들을 찾아줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findByProjectId(Long projectId) {
        return Milestone.findMilestones(projectId, State.ALL);
    }

    /**
     * 완료된 마일스톤들을 찾아 줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findClosedMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.CLOSED);
    }

    /**
     * 미완료된 마일스톤들을 찾아 줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findOpenMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.OPEN);
    }

    /**
     * 완료일을 yyyy-MM-dd 형식의 문자열로 변환시킵니다.
     *
     * @return
     */
    public String getDueDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    /**
     * sort와 direction이 없을 때는 DEFAULT_SORTER 기준으로 오른차순으로 정렬합니다.
     *
     * @param projectId
     * @param state
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId,
                                                 State state) {
        return findMilestones(projectId, state, DEFAULT_SORTER, Direction.ASC);
    }

    /**
     * OrderParam이 있을 때는 해당 정렬 기준으로 정렬합니다.
     *
     * @param projectId
     * @param state
     * @param sort
     * @param direction
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId,
                                                 State state, String sort, Direction direction) {
        OrderParams orderParams = new OrderParams().add(sort, direction);
        SearchParams searchParams = new SearchParams().add("project.id", projectId, Matching.EQUALS);
        if (state == null) {
            state = State.ALL;
        }
        switch (state) {
            case OPEN:
                searchParams.add("numOpenIssues", 0, Matching.GT);
                break;
            case CLOSED:
                searchParams.add("numOpenIssues", 0, Matching.EQUALS);
                break;
        }
        return FinderTemplate.findBy(orderParams, searchParams, find);
    }

    public void updateWith(Milestone newMilestone) {
        this.contents = newMilestone.contents;
        this.title = newMilestone.title;
        this.dueDate = newMilestone.dueDate;
    }

    /**
     * 마일스톤의 목록을 제공합니다.
     *
     * @return
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (Milestone milestone : findMilestones(projectId, State.ALL, "title", Direction.ASC)) {
            options.put(milestone.id.toString(), milestone.title);
        }
        return options;
    }

    public void add(Issue issue) {
        findIssuesNUpdateTotalCount();
        checkIssueState(issue, 1);
        this._save();
    }

    public void updateIssueInfo() {
        List<Issue> issues = findIssuesNUpdateTotalCount();

        this.numOpenIssues = 0;
        this.numClosedIssues = 0;

        for (Issue issue : issues) {
            checkIssueState(issue, 1);
        }

        this.completionRate = calculateCompletionRate(this.numTotalIssues, this.numClosedIssues);
        update(this.id);
    }

    public void delete(Issue issue) {
        this.numTotalIssues -= 1;
        checkIssueState(issue, -1);
        this._save();
    }

    private void checkIssueState(Issue issue, int count) {
        if (issue.isOpen()) {
            this.numOpenIssues += count;
        } else {
            this.numClosedIssues += count;
        }
    }

    private void _save() {
        this.completionRate = calculateCompletionRate(this.numTotalIssues, this.numClosedIssues);
        this.save();
    }

    /**
     * 마일스톤에 연결된 이슈들을 찾아주며 해당 마일스톤에 할당된 이슈의 총 갯수를 update 해준다.
     *
     * @return
     */
    private List<Issue> findIssuesNUpdateTotalCount() {
        List<Issue> issues = Issue.findByMilestoneId(this.id);
        this.numTotalIssues = issues.size();
        return issues;
    }
}
