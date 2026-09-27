CREATE SEQUENCE IF NOT EXISTS organization_id_seq;
CREATE SEQUENCE IF NOT EXISTS membership_id_seq;
CREATE SEQUENCE IF NOT EXISTS staff_type_id_seq;
ALTER TABLE organization ADD CONSTRAINT fk_organization_owner_membership FOREIGN KEY (owner_membership_id) REFERENCES membership (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE membership ADD CONSTRAINT fk_membership_organization FOREIGN KEY (organization_id) REFERENCES organization (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE staff_type ADD CONSTRAINT fk_staff_type_organization FOREIGN KEY (organization_id) REFERENCES organization (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE membership ADD CONSTRAINT fk_membership_staff_type FOREIGN KEY (staff_type_id) REFERENCES staff_type (id) ON DELETE RESTRICT;
