package entities;

/**
 * Represents an external job listing fetched from a third-party API
 * (Remotive, Arbeitnow, Jobicy, RemoteOK).
 */
public class ExternalJob {

    private String source;       // API source: "Remotive", "Arbeitnow", "Jobicy", "RemoteOK"
    private String title;
    private String company;
    private String location;
    private String salary;       // formatted salary string (may be empty)
    private String jobType;      // FULL-TIME, PART-TIME, CONTRACT, FREELANCE, INTERNSHIP, ON-SITE
    private String category;     // e.g. "Software Development", "Design"
    private String url;          // direct link to view/apply
    private String description;  // short snippet
    private String publishedAt;  // ISO date or human-readable

    public ExternalJob() {}

    public ExternalJob(String source, String title, String company, String location,
                       String salary, String jobType, String category, String url,
                       String description, String publishedAt) {
        this.source = source;
        this.title = title;
        this.company = company;
        this.location = location;
        this.salary = salary;
        this.jobType = jobType;
        this.category = category;
        this.url = url;
        this.description = description;
        this.publishedAt = publishedAt;
    }

    // ── Getters & Setters ──

    public String getSource()       { return source; }
    public void setSource(String s) { this.source = s; }

    public String getTitle()        { return title; }
    public void setTitle(String t)  { this.title = t; }

    public String getCompany()         { return company; }
    public void setCompany(String c)   { this.company = c; }

    public String getLocation()         { return location; }
    public void setLocation(String l)   { this.location = l; }

    public String getSalary()           { return salary; }
    public void setSalary(String s)     { this.salary = s; }

    public String getJobType()          { return jobType; }
    public void setJobType(String jt)   { this.jobType = jt; }

    public String getCategory()         { return category; }
    public void setCategory(String c)   { this.category = c; }

    public String getUrl()              { return url; }
    public void setUrl(String u)        { this.url = u; }

    public String getDescription()      { return description; }
    public void setDescription(String d){ this.description = d; }

    public String getPublishedAt()      { return publishedAt; }
    public void setPublishedAt(String p){ this.publishedAt = p; }

    @Override
    public String toString() {
        return String.format("[%s] %s @ %s (%s)", source, title, company, jobType);
    }
}
