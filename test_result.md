#====================================================================================================
# START - Testing Protocol - DO NOT EDIT OR REMOVE THIS SECTION
#====================================================================================================

# THIS SECTION CONTAINS CRITICAL TESTING INSTRUCTIONS FOR BOTH AGENTS
# BOTH MAIN_AGENT AND TESTING_AGENT MUST PRESERVE THIS ENTIRE BLOCK

# Communication Protocol:
# If the `testing_agent` is available, main agent should delegate all testing tasks to it.
#
# You have access to a file called `test_result.md`. This file contains the complete testing state
# and history, and is the primary means of communication between main and the testing agent.
#
# Main and testing agents must follow this exact format to maintain testing data. 
# The testing data must be entered in yaml format Below is the data structure:
# 
## user_problem_statement: {problem_statement}
## backend:
##   - task: "Task name"
##     implemented: true
##     working: true  # or false or "NA"
##     file: "file_path.py"
##     stuck_count: 0
##     priority: "high"  # or "medium" or "low"
##     needs_retesting: false
##     status_history:
##         -working: true  # or false or "NA"
##         -agent: "main"  # or "testing" or "user"
##         -comment: "Detailed comment about status"
##
## frontend:
##   - task: "Task name"
##     implemented: true
##     working: true  # or false or "NA"
##     file: "file_path.js"
##     stuck_count: 0
##     priority: "high"  # or "medium" or "low"
##     needs_retesting: false
##     status_history:
##         -working: true  # or false or "NA"
##         -agent: "main"  # or "testing" or "user"
##         -comment: "Detailed comment about status"
##
## metadata:
##   created_by: "main_agent"
##   version: "1.0"
##   test_sequence: 0
##   run_ui: false
##
## test_plan:
##   current_focus:
##     - "Task name 1"
##     - "Task name 2"
##   stuck_tasks:
##     - "Task name with persistent issues"
##   test_all: false
##   test_priority: "high_first"  # or "sequential" or "stuck_first"
##
## agent_communication:
##     -agent: "main"  # or "testing" or "user"
##     -message: "Communication message between agents"

# Protocol Guidelines for Main agent
#
# 1. Update Test Result File Before Testing:
#    - Main agent must always update the `test_result.md` file before calling the testing agent
#    - Add implementation details to the status_history
#    - Set `needs_retesting` to true for tasks that need testing
#    - Update the `test_plan` section to guide testing priorities
#    - Add a message to `agent_communication` explaining what you've done
#
# 2. Incorporate User Feedback:
#    - When a user provides feedback that something is or isn't working, add this information to the relevant task's status_history
#    - Update the working status based on user feedback
#    - If a user reports an issue with a task that was marked as working, increment the stuck_count
#    - Whenever user reports issue in the app, if we have testing agent and task_result.md file so find the appropriate task for that and append in status_history of that task to contain the user concern and problem as well 
#
# 3. Track Stuck Tasks:
#    - Monitor which tasks have high stuck_count values or where you are fixing same issue again and again, analyze that when you read task_result.md
#    - For persistent issues, use websearch tool to find solutions
#    - Pay special attention to tasks in the stuck_tasks list
#    - When you fix an issue with a stuck task, don't reset the stuck_count until the testing agent confirms it's working
#
# 4. Provide Context to Testing Agent:
#    - When calling the testing agent, provide clear instructions about:
#      - Which tasks need testing (reference the test_plan)
#      - Any authentication details or configuration needed
#      - Specific test scenarios to focus on
#      - Any known issues or edge cases to verify
#
# 5. Call the testing agent with specific instructions referring to test_result.md
#
# IMPORTANT: Main agent must ALWAYS update test_result.md BEFORE calling the testing agent, as it relies on this file to understand what to test next.

#====================================================================================================
# END - Testing Protocol - DO NOT EDIT OR REMOVE THIS SECTION
#====================================================================================================



#====================================================================================================
# Testing Data - Main Agent and testing sub agent both should log testing data below this section
#====================================================================================================

user_problem_statement: |
  Add the Pro-tier Translation feature for Anteroom. Free users see a locked
  UI and get a paywall on tap. Pro users can translate any generated brief
  between English and Spanish — both the on-screen brief and a fresh
  translated PDF export. Language chip on brief-draft (before generate) and a
  language toggle on brief-view (after generate).

backend:
  - task: "POST /api/briefs/{brief_id}/translate — translate brief content EN↔ES"
    implemented: true
    working: "NA"
    file: "/app/backend/server.py, /app/backend/brieftranslator.py"
    stuck_count: 0
    priority: "high"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            New endpoint. Requires auth. Body { target_language: 'en'|'es' }.
            Rejects with 400 for invalid languages. Returns the brief with
            `content_translations[target_language]` cached. Idempotent — if
            the target == source_language OR the translation is already
            cached, it returns the brief without calling the LLM again.
            Uses Gemini 3.1 Pro Preview via emergentintegrations; strict rules
            keep medication names / doses / dates / patient name verbatim.

  - task: "GET /api/briefs/{brief_id}/pdf?lang=xx — localized PDF export"
    implemented: true
    working: "NA"
    file: "/app/backend/server.py, /app/backend/briefpdf.py"
    stuck_count: 0
    priority: "high"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            PDF endpoint now accepts `?lang=en|es`. Renders using the cached
            translation, with localized section titles and the free-tier
            ribbon translated. Falls back to source content silently if a
            translation is not cached.

  - task: "GET /api/public/briefs/{share_token}?lang=xx — localized share view"
    implemented: true
    working: "NA"
    file: "/app/backend/server.py"
    stuck_count: 0
    priority: "medium"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            Public share page respects ?lang= and includes a language switcher
            for whichever translations are cached. No auth required.

  - task: "BriefOut exposes source_language, content_translations, available_languages"
    implemented: true
    working: "NA"
    file: "/app/backend/server.py"
    stuck_count: 0
    priority: "high"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            /api/briefs/{id} and list endpoints now return source_language
            (set at generate-time from user.language, defaulting to 'en'),
            content_translations (cache map), and available_languages.

frontend:
  - task: "brief-draft language chip picker (English/Español) with Pro gate"
    implemented: true
    working: "NA"
    file: "/app/frontend/app/brief-draft.tsx"
    stuck_count: 0
    priority: "high"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            Chips: brief-draft-lang-en / brief-draft-lang-es. Free users
            tapping the non-profile language route to /paywall?trigger=translate.
            After generate, if the picked language differs from the source we
            auto-call briefs.translate() before navigating.

  - task: "brief-view language toggle with in-place translation"
    implemented: true
    working: "NA"
    file: "/app/frontend/app/brief-view.tsx"
    stuck_count: 0
    priority: "high"
    needs_retesting: true
    status_history:
        - working: "NA"
          agent: "main"
          comment: |
            Chips: brief-view-lang-en / brief-view-lang-es. Free users hit
            the paywall. Pro users trigger translate (with spinner) and the
            page re-renders in-place from content_translations[lang]. The
            "Download PDF" button includes the active `lang` param.

metadata:
  created_by: "main_agent"
  version: "1.0"
  test_sequence: 0
  run_ui: false

test_plan:
  current_focus:
    - "POST /api/briefs/{brief_id}/translate — translate brief content EN↔ES"
    - "GET /api/briefs/{brief_id}/pdf?lang=xx — localized PDF export"
    - "brief-draft language chip picker (English/Español) with Pro gate"
    - "brief-view language toggle with in-place translation"
  stuck_tasks: []
  test_all: false
  test_priority: "high_first"

agent_communication:
    - agent: "main"
      message: |
        Please test the new Translation feature.

        Backend flow:
        1) Login as test1@anteroom.dev / password123 (language=en).
        2) Create a brief, upload the sample photo(s) already present under
           /app/backend/test-data/ if available OR any valid JPG.
        3) POST /api/briefs/{id}/generate → verify response has
           source_language='en', content_translations={}, available_languages=['en'].
        4) POST /api/briefs/{id}/translate {target_language: 'es'} →
           verify a Spanish 'referral_reason', flagged_items[*] and
           allergy reaction strings are Spanish, but patient.name, dob,
           id_number and medications[*].name/dose/frequency are byte-for-byte
           identical to the English version. `available_languages` becomes ['en','es'].
        5) POST /api/briefs/{id}/translate {target_language: 'es'} AGAIN →
           should be an instant no-LLM response (same content_translations).
        6) POST /api/briefs/{id}/translate {target_language: 'en'} (source lang) →
           no-op, no error.
        7) POST /api/briefs/{id}/translate {target_language: 'fr'} → 400.
        8) GET /api/briefs/{id}/pdf?lang=es → returns application/pdf bytes.
        9) GET /api/public/briefs/{share_token}?lang=es → returns Spanish HTML
           with a language switcher chip strip.

        Frontend flow (web preview):
        1) Login flow, go to brief-draft with an existing draft that has
           at least one photo.
        2) See the "Brief language" section with chips English / Español.
        3) As a FREE user, tap Español → should route to /paywall?trigger=translate.
        4) On brief-view, verify the LANGUAGE row is visible with the two chips.
        5) As a FREE user, tap Español → paywall. As a Pro user, tap Español →
           spinner, then the page content updates in Spanish, and Download PDF
           now downloads a Spanish PDF.
