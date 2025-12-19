/**
 * A "Person" structure.
 */
const Person(String? firstName,
             String? middleName,
             String? lastName,
             String? phone      = Null,
             String? email      = Null,
            ) {

    Person with(String? firstName  = Null,
                String? middleName = Null,
                String? lastName   = Null,
                String? phone      = Null,
                String? email      = Null,
               ) {
        return new Person(
            firstName  = nullableString(firstName  ?: this.firstName),
            middleName = nullableString(middleName ?: this.middleName),
            lastName   = nullableString(lastName   ?: this.lastName),
            phone      = nullableString(phone      ?: this.phone),
            email      = nullableString(email      ?: this.email),
        );
    }

    String displayName.get() {
        StringBuffer buf = new StringBuffer();
        if (String name ?= firstName, !name.empty) {
            name.appendTo(buf);
        }
        if (String name ?= middleName, !name.empty) {
            if (!buf.empty) {
                buf += ' ';
            }
            name.appendTo(buf);
        }
        if (String name ?= lastName, !name.empty) {
            if (!buf.empty) {
                buf += ' ';
            }
            name.appendTo(buf);
        }
        return buf.empty ? "<no-name>" : buf.toString();
    }

    static String? nullableString(String? s) {
        return (s?.empty : False) ? Null : s;
    }

    @Override
    String toString() {
        String s = displayName;
        if (phone != Null) {
            s = $"{s}\n    phone: {phone}";
        }
        if (email != Null) {
            s = $"{s}\n    email: {email}";
        }
        return s;
    }
}